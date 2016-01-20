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
import geotrellis.spark.io.accumulo._
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

import org.apache.accumulo.core.client.security.tokens.PasswordToken
import org.apache.commons.io.filefilter._

import org.apache.spark._
import org.apache.spark.rdd._
import spray.json.DefaultJsonProtocol._

import com.github.nscala_time.time.Imports._
import org.joda.time.Days

import java.io.File

object NEXIngest {
  val catalogPath = "/Volumes/Transcend/data/workshop/jan2016/data/nex-catalog"
  val targetLayoutScheme = ZoomedLayoutScheme(WebMercator, 256)
  // Index by every 1 month
  val indexMethod = ZCurveKeyIndexMethod.byMillisecondResolution(1000L * 60 * 60 * 24 * 30)

  def main(args: Array[String]): Unit = {
    // Setup Spark to use Kryo serializer.
    val conf =
      new SparkConf()
        .setIfMissing("spark.master", "local[8]")
        .setAppName("NEX Ingest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.hadoop.KryoRegistrator")

    implicit val sc = new SparkContext(conf)

    try {
      args(0) match {
        case "local" =>
          runLocal("Climate_CCSM4-RCP45-Temperature-Max", "file:///Volumes/Transcend/data/workshop/jan2016/data/nex-sourcetiles/ccsm4-rcp45")
          runLocal("Climate_CCSM4-RCP85-Temperature-Max", "file:///Volumes/Transcend/data/workshop/jan2016/data/nex-sourcetiles/ccsm4-rcp85")
          // Pause to wait to close the spark context,
          // so that you can check out the UI at http://localhost:4040
          println("Hit enter to exit.")
          readLine()
        case "custom-local" =>
          runCustomLocal("Climate_CCSM4-RCP45-Temperature-Max", "file:///Volumes/Transcend/data/workshop/jan2016/data/nex-sourcetiles/ccsm4-rcp45")
//          runLocal("Climate_CCSM4-RCP85-Temperature-Max", "file:///Volumes/Transcend/data/workshop/jan2016/data/nex-sourcetiles/ccsm4-rcp85")
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
          if(args(1) == "1") {
            runAccumulo("RCP45-Temperature-Max", "nex-dcp30-ensemble-geotiffs", "tasmax/rcp45")
          } else if(args(1) == "2") {
            runAccumulo("RCP45-Temperature-Min", "nex-dcp30-ensemble-geotiffs", "tasmin/rcp45")
          } else if(args(1) == "3") {
            runAccumulo("RCP85-Temperature-Max", "nex-dcp30-ensemble-geotiffs", "tasmax/rcp85")
          } else {
            runAccumulo("RCP85-Temperature-Min", "nex-dcp30-ensemble-geotiffs", "tasmin/rcp85")
          }
        case "custom-accumulo" =>
          if(args(1) == "1") {
            runCustomAccumulo("RCP45-Temperature-Max", "nex-dcp30-ensemble-geotiffs", "tasmax/rcp45")
          } else if(args(1) == "2") {
            runCustomAccumulo("RCP45-Temperature-Min", "nex-dcp30-ensemble-geotiffs", "tasmin/rcp45")
          } else if(args(1) == "3") {
            runCustomAccumulo("RCP85-Temperature-Max", "nex-dcp30-ensemble-geotiffs", "tasmax/rcp85")
          } else {
            runAccumulo("RCP85-Temperature-Min", "nex-dcp30-ensemble-geotiffs", "tasmin/rcp85")
          }
      }
    } finally {
      println("Hit enter to exit.")
      readLine()
      sc.stop()
    }
  }

  def processSourceTiles(
    layerName: String,
    sourceTiles: RDD[(SpaceTimeInputKey, Tile)],
    attributeStore: AttributeStore[JsonFormat],
    writer: Writer[LayerId, RasterRDD[SpaceTimeKey]],
    partitionTo: Option[Int] = None
  ): Unit = {
    // We really only need UShort resolution with fahrenheit values.
    val fahrenheitSourceTiles =
      sourceTiles.mapValues { tile =>
        tile.convert(TypeShort).mapDouble { v =>
          if(isData(v)) {
            (v * 9) / 5 - 459.67
          } else { v }
        }
      }

    val (_, metadataWithoutCrs) =
      RasterMetaData.fromRdd(fahrenheitSourceTiles, FloatingLayoutScheme(512))

    val metadata = metadataWithoutCrs.copy(crs = LatLng)

    val tiled =
      partitionTo match {
        case Some(numPartitions) =>
          fahrenheitSourceTiles
            .tileToLayout[SpaceTimeKey](metadata, Tiler.Options(resampleMethod = NearestNeighbor, partitioner = new HashPartitioner(100)))
        case None =>
          fahrenheitSourceTiles
            .tileToLayout[SpaceTimeKey](metadata, Tiler.Options(resampleMethod = NearestNeighbor))
      }

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

  def s3SourceTiles(bucket: String, prefix: String)(implicit sc: SparkContext): RDD[(SpaceTimeInputKey, Tile)] = {
    val conf = sc.hadoopConfiguration
    S3InputFormat.setBucket(conf, bucket)
    S3InputFormat.setPrefix(conf, prefix)
    S3InputFormat.setMaxKeys(conf, 100)
    SpaceTimeGeoTiffS3InputFormat.setTimeTag(conf, "ISO_TIME")
    SpaceTimeGeoTiffS3InputFormat.setTimeFormat(conf, "yyyy-MM-dd'T'HH:mm:ss")

    sc.newAPIHadoopRDD(
      conf,
      classOf[SpaceTimeGeoTiffS3InputFormat],
      classOf[SpaceTimeInputKey],
      classOf[Tile]
    ).repartition(4000)
  }

  def runAccumulo(layerName: String, bucket: String, prefix: String)(implicit sc: SparkContext): Unit = {
    val instanceName = "geotrellis-accumulo-cluster"
    val zooKeeper = "zookeeper.service.geotrellis-spark.internal"
    val user = "root"
    val password = new PasswordToken("secret")

    val instance = AccumuloInstance(instanceName, zooKeeper, user, password)
    val attributeStore = AccumuloAttributeStore(instance.connector)
    val writer = AccumuloLayerWriter[SpaceTimeKey, Tile, RasterMetaData](instance, "tiles", indexMethod)

    processSourceTiles(layerName, s3SourceTiles(bucket, prefix), attributeStore, writer)
  }

  def runS3(layerName: String, bucket: String, prefix: String)(implicit sc: SparkContext): Unit = {
    val attributeStore = S3AttributeStore("geotrellis-climate-catalogs", "ensemble-temperature-catalog")
    val writer = S3LayerWriter[SpaceTimeKey, Tile, RasterMetaData](attributeStore, indexMethod)

    processSourceTiles(layerName, s3SourceTiles(bucket, prefix), attributeStore, writer)
  }

  def runLocal(layerName: String, tilesDir: String)(implicit sc: SparkContext): Unit = {
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
    val writer = FileLayerWriter[SpaceTimeKey, Tile, RasterMetaData](attributeStore, indexMethod)

    processSourceTiles(layerName, sourceTiles, attributeStore, writer, Some(100))
  }

  def runCustomLocal(layerName: String, tilesDir: String)(implicit sc: SparkContext): Unit = {
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

    val instance = AccumuloInstance("gis", "localhost", "root", new PasswordToken("secret"))
    val writer = new CustomAccumuloLayerWriter[Tile, RasterMetaData](instance, "test_custom")
//    val writer = AccumuloLayerWriter[SpaceTimeKey, Tile, RasterMetaData](instance, "test_custom", ZCurveKeyIndexMethod.byYear)

    processSourceTiles(layerName, sourceTiles, writer.attributeStore, writer, Some(100))
  }

  def runCustomAccumulo(layerName: String, bucket: String, prefix: String)(implicit sc: SparkContext): Unit = {
    val instanceName = "geotrellis-accumulo-cluster"
    val zooKeeper = "zookeeper.service.geotrellis-spark.internal"
    val user = "root"
    val password = new PasswordToken("secret")

    val instance = AccumuloInstance(instanceName, zooKeeper, user, password)
    val attributeStore = AccumuloAttributeStore(instance.connector)
    val writer = new CustomAccumuloLayerWriter[Tile, RasterMetaData](instance, "tiles")

    processSourceTiles(layerName, s3SourceTiles(bucket, prefix), attributeStore, writer)
  }

}
