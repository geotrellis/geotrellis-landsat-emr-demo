package demo

import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.reproject._
import geotrellis.raster.resample._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.op.stats._
import geotrellis.spark._
import geotrellis.spark.ingest._
import geotrellis.spark.io._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.file._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.hadoop.formats._
import geotrellis.spark.io.index._
import geotrellis.spark.io.json._
import geotrellis.spark.io.s3._
import geotrellis.spark.tiling._
import geotrellis.proj4._
import spray.json._

import org.apache.commons.io.filefilter._

import org.apache.spark._
import org.apache.spark.rdd._
import spray.json.DefaultJsonProtocol._

import com.github.nscala_time.time.Imports._
import org.joda.time.Days

import java.io.File

object NEXIngest {
  val catalogPath = "/Users/rob/proj/workshops/apple/data/landsat-catalog"

  val targetLayoutScheme = ZoomedLayoutScheme(WebMercator, 256)

  def main(args: Array[String]): Unit = {
    // Setup Spark to use Kryo serializer.
    val conf =
      new SparkConf()
        .setIfMissing("spark.master", "local[8]")
        .setAppName("Landsat Ingest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.hadoop.KryoRegistrator")

    implicit val sc = new SparkContext(conf)

    try {
      args(0) match {
        case "local" =>
          runLocal("Climate_CCSM4-RCP45-Temperature-Max", "file:///Users/rob/proj/climate/data/fossdem/tile_export/ccsm4-rcp45")
          runLocal("Climate_CCSM4-RCP85-Temperature-Max", "file:///Users/rob/proj/climate/data/fossdem/tile_export/ccsm4-rcp85")
          // Pause to wait to close the spark context,
          // so that you can check out the UI at http://localhost:4040
          println("Hit enter to exit.")
          readLine()
        case "s3" =>
          runS3("RCP45-Temperature-Max", "nex-dcp30-ensemble-geotiffs", "tasmax/rcp45")
          runS3("RCP45-Temperature-Min", "nex-dcp30-ensemble-geotiffs", "tasmin/rcp45")
          runS3("RCP85-Temperature-Max", "nex-dcp30-ensemble-geotiffs", "tasmax/rcp85")
          runS3("RCP85-Temperature-Min", "nex-dcp30-ensemble-geotiffs", "tasmin/rcp85")
        case "accumulo" =>
          ???
      }
    } finally {
      sc.stop()
    }
  }

  def processSourceTiles(
    layerName: String,
    sourceTiles: RDD[(SpaceTimeInputKey, Tile)],
    attributeStore: AttributeStore[JsonFormat],
    writer: Writer[LayerId, RasterRDD[SpaceTimeKey]]
  ): Unit = {
    val (_, metadataWithoutCrs) =
      RasterMetaData.fromRdd(sourceTiles, FloatingLayoutScheme(512))

    val metadata = metadataWithoutCrs.copy(crs = LatLng)

    val tiled =
      sourceTiles
        .tileToLayout[SpaceTimeKey](metadata, Tiler.Options(resampleMethod = NearestNeighbor, partitioner = new HashPartitioner(100)))


    val (zoom, layer) =
      RasterRDD(tiled, metadata)
        .reproject(targetLayoutScheme, bufferSize = 30, Reproject.Options(method = Bilinear, errorThreshold = 0))

    val halfZoom = zoom / 2

    val lastRdd =
      Pyramid.upLevels(layer, targetLayoutScheme, zoom, Bilinear) { (rdd, zoom) =>
        writer.write(LayerId(layerName, zoom), rdd)
        if(zoom == halfZoom) {
          // Store breaks
          val breaks =
            rdd
              .map{ case (key, tile) => tile.histogram }
              .reduce { (h1, h2) => FastMapHistogram.fromHistograms(Array(h1,h2)) }
              .getQuantileBreaks(20)

          attributeStore.write(LayerId(layerName, 0), "breaks", breaks)
        }
      }

    attributeStore.write(LayerId(layerName, 0), "times", lastRdd.map(_._1.instant).collect.toArray)
  }

  def runS3(layerName: String, bucket: String, prefix: String)(implicit sc: SparkContext): Unit = {
    val conf = sc.hadoopConfiguration
    S3InputFormat.setBucket(conf, bucket)
    S3InputFormat.setPrefix(conf, prefix)
    S3InputFormat.setMaxKeys(conf, 10)
    SpaceTimeGeoTiffS3InputFormat.setTimeTag(conf, "ISO_TIME")
    SpaceTimeGeoTiffS3InputFormat.setTimeFormat(conf, "yyyy-MM-dd'T'HH:mm:ss")

    val sourceTiles =
      sc.newAPIHadoopRDD(
        conf,
        classOf[SpaceTimeGeoTiffS3InputFormat],
        classOf[SpaceTimeInputKey],
        classOf[Tile]
      )

    val attributeStore = S3AttributeStore("geotrellis-climate-catalogs", "ensemble-catalog")
    val writer = S3LayerWriter[SpaceTimeKey, Tile, RasterMetaData](attributeStore, ZCurveKeyIndexMethod.byMillisecondResolution(1000 * 60 * 60 * 24 * 30))

    processSourceTiles(layerName, sourceTiles, attributeStore, writer)
  }

  def runLocal(layerName: String, tilesDir: String)(implicit sc: SparkContext): Unit = {
    // Grab the source tiles
    val conf = sc.hadoopConfiguration.withInputDirectory(tilesDir)
    SpaceTimeGeoTiffInputFormat.setTimeTag(conf, "ISO_TIME")
    SpaceTimeGeoTiffInputFormat.setTimeFormat(conf, "yyyy-MM-dd'T'HH:mm:ss")

    val sourceTiles =
      sc.newAPIHadoopRDD(
        conf,
        classOf[SpaceTimeGeoTiffInputFormat],
        classOf[SpaceTimeInputKey],
        classOf[Tile]
      )

    val attributeStore = FileAttributeStore(catalogPath)
    val writer = FileLayerWriter[SpaceTimeKey, Tile, RasterMetaData](attributeStore, ZCurveKeyIndexMethod.byMillisecondResolution(1000 * 60 * 60 * 24 * 30))

    processSourceTiles(layerName, sourceTiles, attributeStore, writer)
  }
}
