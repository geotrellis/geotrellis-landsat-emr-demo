package demo

import com.github.nscala_time.time.Imports._
import geotrellis.vector._
import geotrellis.vector.io._
import geotrellis.raster._
import geotrellis.raster.split._
import geotrellis.raster.io.geotiff.reader._
import geotrellis.raster.resample._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.index._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.file._
import geotrellis.spark.io.accumulo._
import geotrellis.spark.util._
import geotrellis.spark.tiling._
import geotrellis.spark.pyramid._
import geotrellis.proj4._
import geotrellis.spark.ingest._
import com.azavea.landsatutil.{S3Client => lsS3Client, _}

import spray.json.DefaultJsonProtocol._
import spray.json._
import org.apache.spark._
import org.apache.spark.rdd._
import org.apache.accumulo.core.client.security.tokens._

import java.net._
import java.io._
import org.apache.commons.io.IOUtils


object LandsatSource extends Logging {
  def localCache(cacheFile: File, freshStream: => InputStream): InputStream = {
    if (cacheFile.exists) {
       new FileInputStream(cacheFile)
    } else {
      cacheFile.getParentFile.mkdirs()
      val out = new FileOutputStream(cacheFile)
      try {
        IOUtils.copy(freshStream, out)
        new FileInputStream(cacheFile)
      } finally { out.close; freshStream.close }
    }
  }

  def localCache(cacheFile: Option[File], freshStream: => InputStream): InputStream = {
    cacheFile match {
      case Some(file) => localCache(file, freshStream)
      case None => freshStream
    }
  }

  // this will allow us to use only one s3 client per executor
  @transient lazy val s3client: S3Client = S3Client.default
  def s3(bandsWanted: Seq[String], image: LandsatImage, cacheDir: Option[File] = None): ProjectedRaster[MultibandTile] = {
    val tifs =
      for (band <- bandsWanted) yield {
        val uri = new URI(image.bandUri(band))
        logger.info(s"Getting '$uri' for band '$band'")
        val bucket = uri.getAuthority
        val prefix = uri.getPath.drop(1)
        val cacheFile = cacheDir.map (new File(_, prefix))
        val bytes = IOUtils.toByteArray(
          localCache(cacheFile, s3client.getObject(bucket, prefix).getObjectContent)
        )
        GeoTiffReader.readSingleband(bytes)
      }
    val tiles = tifs.map(_.tile).toArray
    val extent = tifs.head.extent
    val crs = tifs.head.crs
    ProjectedRaster(MultibandTile(tiles), extent, crs)
  }

  def httpTarBall(bandsWanted: Seq[String], img: LandsatImage, cacheDir: Option[File]): ProjectedRaster[MultibandTile] = {
    val url = new URL(img.googleUrl)
    val bytes = IOUtils.toByteArray(
      localCache(cacheDir.map(new File(_, url.getPath)), url.openStream)
    )
    val (mtl, raster) = Fetch.fromTar(new ByteArrayInputStream(bytes), bandsWanted)
    raster
  }
}


object LandsatIngest extends Logging {

  /** Calculate the layer metadata for the incoming landsat images
    *
    * Normally we would have no information about the incoming rasters and we be forced
    * to use [[TileLayerMetadata.fromRdd]] to collect it before we could tile the imagery.
    * But in this case the pre-query from scala-landsatutil is providing enough
    * information that the metadata can be calculated.
    *
    * Collecting metadata before tiling step requires either reading the data twice
    * or caching records in spark memory. In either case avoiding collection is a performance boost.
    */

  def calculateTileLayerMetadata(maxZoom: Int, destCRS: CRS, images: Seq[LandsatImage]) = {
    val layoutDefinition = ZoomedLayoutScheme.layoutForZoom(maxZoom, destCRS.worldExtent, 256)
    val imageExtent = images.map(_.footprint.envelope).reduce(_ combine _).reproject(LatLng, destCRS)
    val dateMin = images.map(_.aquisitionDate).min
    val dateMax = images.map(_.aquisitionDate).max
    val GridBounds(colMin, rowMin, colMax, rowMax) = layoutDefinition.mapTransform(imageExtent)
    TileLayerMetadata(
      cellType = UShortCellType,
      layout = layoutDefinition,
      extent = imageExtent,
      crs = destCRS,
      bounds = KeyBounds(
        SpaceTimeKey(colMin, rowMin, dateMin),
        SpaceTimeKey(colMax, rowMax, dateMax))
    )
  }

  /** Transforms a collection of Landsat image descriptions into RDD of MultibandTiles.
    * Each landsat scene is downloaded, reprojected and then split into 256x256 chunks.
    * Chunking the scene allows for greater parallism and reduces memory pressure
    * produces by processing each partition.
    */
  def fetch(
    images: Seq[LandsatImage],
    source: LandsatImage => ProjectedRaster[MultibandTile]
  )(implicit sc: SparkContext): RDD[(TemporalProjectedExtent, MultibandTile)] = {
    sc.parallelize(images, images.length) // each image gets its own partition
      .mapPartitions({ iter =>
        for {
          img <- iter
          ProjectedRaster(raster, crs) = source(img)
          reprojected = raster.reproject(crs, WebMercator) // reprojection before chunking avoids NoData artifacts
          chunk <- reprojected.split(TileLayout(31, 31, 256, 256), Split.Options(cropped = false, extend = false))
        } yield {
          TemporalProjectedExtent(chunk.extent, WebMercator, img.aquisitionDate) -> chunk.tile
        }
      }, preservesPartitioning = true)
      .repartition(images.length * 8) // Break up each scene into 8 partitions
  }

  /** Accept a list of landsat image descriptors we will ingest into a geotrellis layer.
    * It is expected that the user will generate this list using `Landsat8Query` and prefilter.
    */
  def run(
    layerName: String,
    images: Seq[LandsatImage],
    fetchMethod: LandsatImage => ProjectedRaster[MultibandTile],
    writer: LayerWriter[LayerId]
  )(implicit sc: SparkContext): Unit = {
    // Our dataset can span UTM zones, we must reproject the tiles individually to common projection
    val maxZoom = 13 // We know this ahead of time based on Landsat resolution
    val destCRS = WebMercator
    val resampleMethod = Bilinear
    val layoutScheme = ZoomedLayoutScheme(destCRS, 256)
    val reprojected = fetch(images, fetchMethod)
    val tileLayerMetadata = calculateTileLayerMetadata(maxZoom, destCRS, images)
    logger.info("sTileLayerMetadata calculated: $tileLayerMetadata")
    val tiledRdd = reprojected.tileToLayout(tileLayerMetadata, resampleMethod)
    val rdd = new ContextRDD(tiledRdd, tileLayerMetadata)

    Pyramid.upLevels(rdd, layoutScheme, maxZoom, 1, resampleMethod){ (rdd, zoom) =>
      writer.write(LayerId(layerName, zoom), rdd, ZCurveKeyIndexMethod.byDay)

      if (zoom == 1) {
        // Store attributes common across zooms for catalog to see
        val id = LayerId(layerName, 0)
        writer.attributeStore.write(id, "times",
          rdd
            .map(_._1.instant)
            .countByValue
            .keys.toArray
            .sorted)
        writer.attributeStore.write(id, "extent",
          (rdd.metadata.extent, rdd.metadata.crs))
      }
    }
  }
}

object LandsatIngestMain extends Logging {

  def main(args: Array[String]): Unit = {
    logger.info(s"Arguments: ${args.toSeq}")
    val config = Config.parse(args)
    logger.info(s"Config: $config")

    val s3Client = com.azavea.landsatutil.S3Client()

    val images = Landsat8Query()
      .withStartDate(new DateTime(2012, 1, 12, 0, 0, 0))
      .withEndDate(new DateTime(2015, 11, 5, 0, 0, 0))
      .withMaxCloudCoverage(config.maxCloudCoverage)
      .intersects(config.polygon)
      .collect()
      .filter{ img =>
        val exists = s3Client.imageExists(img)
        logger.info(s"Scene not found:  ${img.baseS3Path}")
        exists
      }

    logger.info(s"Found ${images.length} landsat images")

    val bandsWanted = Array(
      // Red, Green, Blue
      "4", "3", "2",
      // Near IR
      "5",
      "QA")

    implicit val sc = SparkUtils.createSparkContext("GeoTrellis Landsat Ingest", new SparkConf(true))

    try {
      /* TODO if the layer exists the ingest will fail, we need to use layer updater*/
      LandsatIngest.run(
        layerName = config.layerName,
        images = config.limit.fold(images)(images.take(_)),
        fetchMethod = LandsatSource.s3(bandsWanted, (_: LandsatImage), config.cache),
        writer = config.writer)
    } finally {
      sc.stop()
    }
  }
}
