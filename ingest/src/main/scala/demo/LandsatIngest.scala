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
import geotrellis.spark.io.hbase._
import geotrellis.spark.partition._
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

import scala.util.Try

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
    source: LandsatImage => Option[ProjectedRaster[MultibandTile]]
  )(implicit sc: SparkContext): RDD[(TemporalProjectedExtent, MultibandTile)] = {
    sc.parallelize(images, images.length) // each image gets its own partition
      .mapPartitions({ iter =>
        for {
          img <- iter
          ProjectedRaster(raster, crs) <- source(img).toList
          reprojected = raster.reproject(crs, WebMercator) // reprojection before chunking avoids NoData artifacts
          layoutCols = math.ceil(reprojected.cols.toDouble / 256).toInt
          layoutRows = math.ceil(reprojected.rows.toDouble / 256).toInt
          chunk <- reprojected.split(TileLayout(layoutCols, layoutRows, 256, 256), Split.Options(cropped = false, extend = false))
        } yield {
          TemporalProjectedExtent(chunk.extent, WebMercator, img.aquisitionDate) -> chunk.tile
        }
      }, preservesPartitioning = true)
      .repartition(images.length * 16) // Break up each scene into 16 partitions
  }

  /** Accept a list of landsat image descriptors we will ingest into a geotrellis layer.
    * It is expected that the user will generate this list using `Landsat8Query` and prefilter.
    */
  def run(
    layerName: String,
    images: Seq[LandsatImage],
    fetchMethod: LandsatImage => Option[ProjectedRaster[MultibandTile]],
    writer: LayerWriter[LayerId]
  )(implicit sc: SparkContext): Unit = {
    // Our dataset can span UTM zones, we must reproject the tiles individually to common projection
    val maxZoom = 13 // We know this ahead of time based on Landsat resolution
    val destCRS = WebMercator
    val resampleMethod = NearestNeighbor
    val layoutScheme = ZoomedLayoutScheme(destCRS, 256)
    val indexMethod = ZCurveKeyIndexMethod.byDay
    val reprojected = fetch(images, fetchMethod)
    val tileLayerMetadata = calculateTileLayerMetadata(maxZoom, destCRS, images)
    logger.info("sTileLayerMetadata calculated: $tileLayerMetadata")
    val tiledRdd = reprojected.tileToLayout(tileLayerMetadata, resampleMethod)
    val rdd = new ContextRDD(tiledRdd, tileLayerMetadata)

    Pyramid.upLevels(rdd, layoutScheme, maxZoom, 1, resampleMethod){ (rdd, zoom) =>
      writer.write(LayerId(layerName, zoom), rdd, indexMethod)

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

    implicit val sc = SparkUtils.createSparkContext("GeoTrellis Landsat Ingest", new SparkConf(true))
    val config = Config.parse(args)
    logger.info(s"Config: $config")

    val images = Landsat8Query()
      .withStartDate(config.startDate.toDateTimeAtStartOfDay)
      .withEndDate(config.endDate.toDateTimeAtStartOfDay)
      .withMaxCloudCoverage(config.maxCloudCoverage)
      .intersects(config.bbox)
      .collect()

    logger.info(s"Found ${images.length} landsat images")

    val bandsWanted = Array(
      // Red, Green, Blue
      "4", "3", "2",
      // Near IR
      "5",
      "QA")


    try {
      /* TODO if the layer exists the ingest will fail, we need to use layer updater*/
      LandsatIngest.run(
        layerName = config.layerName,
        images = config.limit.fold(images)(images.take(_)),
        fetchMethod = { img =>
          Try { img.getRasterFromS3(bandsWanted = bandsWanted, hook = config.cacheHook) }
            .recover{ case err => img.getFromGoogle(bandsWanted = bandsWanted, hook = config.cacheHook).raster }
            .toOption
        },
        writer = config.writer)
    } finally {
      sc.stop()
    }
  }
}
