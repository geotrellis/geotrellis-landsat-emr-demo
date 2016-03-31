package demo

import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.reproject._
import geotrellis.raster.resample._
import geotrellis.raster.io.geotiff._
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
  val indexMethod = ZCurveKeyIndexMethod.byMonth

  def main(args: Array[String]): Unit = {
    // Setup Spark to use Kryo serializer.
    val conf =
      new SparkConf()
        .setIfMissing("spark.master", "local[8]")
        .setAppName("NEX Ingest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")

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
    sourceTiles: RDD[(TemporalProjectedExtent, Tile)],
    attributeStore: AttributeStore,
    writer: LayerWriter[LayerId],
    partitionTo: Option[Int] = None
  ): Unit = {
    // We really only need UShort resolution with fahrenheit values.
    val fahrenheitSourceTiles =
      sourceTiles.mapValues { tile =>
        tile.convert(ShortCellType).mapDouble { v =>
          if(isData(v)) {
            (v * 9) / 5 - 459.67
          } else { v }
        }
      }

    val (_, metadataWithoutCrs) =
      TileLayerMetadata.fromRdd[TemporalProjectedExtent, Tile, SpaceTimeKey](fahrenheitSourceTiles, FloatingLayoutScheme(512))

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
      TileLayerRDD(tiled, metadata)
        .reproject(targetLayoutScheme, bufferSize = 30, Reproject.Options(method = Bilinear, errorThreshold = 0))

    val halfZoom = zoom / 2

    val lastRdd =
      Pyramid.upLevels(layer, targetLayoutScheme, zoom, Bilinear) { (rdd, zoom) =>
        writer.write(LayerId(layerName, zoom), rdd, indexMethod)
        if(zoom == halfZoom) {
          // Store breaks
          val breaks =
            rdd
              .map{ case (key, tile) => tile.histogram }
              .reduce { _ merge _ }
              .quantileBreaks(20)

          attributeStore.write(LayerId(layerName, 0), "breaks", breaks)
        }
      }

    // Store the key times so that we can know what time slices we can get out of this layer.
    attributeStore.write(LayerId(layerName, 0), "times", lastRdd.map(_._1.instant).collect.toArray)
  }

  def s3SourceTiles(bucket: String, prefix: String)(implicit sc: SparkContext): RDD[(TemporalProjectedExtent, Tile)] = {
    val conf = sc.hadoopConfiguration
    S3InputFormat.setBucket(conf, bucket)
    S3InputFormat.setPrefix(conf, prefix)
    TemporalGeoTiffS3InputFormat.setTimeTag(conf, "ISO_TIME")
    TemporalGeoTiffS3InputFormat.setTimeFormat(conf, "yyyy-MM-dd'T'HH:mm:ss")

    val tiles =
      sc.newAPIHadoopRDD(
        conf,
        classOf[TemporalGeoTiffS3InputFormat],
        classOf[TemporalProjectedExtent],
        classOf[Tile]
      )

    // Force a repartition, since Spark isn't very used
    // to data this large and can end up throwing OutOfMemory errors
    // if the partitions start too big.
    tiles.repartition(4000)
  }

  def runAccumulo(layerName: String, bucket: String, prefix: String)(implicit sc: SparkContext): Unit = {
    val instanceName = "geotrellis-accumulo-cluster"
    val zooKeeper = "zookeeper.service.geotrellis-spark.internal"
    val user = "root"
    val password = new PasswordToken("secret")

    val instance = AccumuloInstance(instanceName, zooKeeper, user, password)
    val attributeStore = AccumuloAttributeStore(instance.connector)
    val writer = AccumuloLayerWriter(instance, "tiles")
    // val writer = AccumuloLayerWriter[SpaceTimeKey, Tile, TileLayerMetadata](instance, "tiles", indexMethod)

    processSourceTiles(layerName, s3SourceTiles(bucket, prefix), attributeStore, writer)
  }

  def runS3(layerName: String, bucket: String, prefix: String)(implicit sc: SparkContext): Unit = {
    val attributeStore = S3AttributeStore("geotrellis-climate-catalogs", "ensemble-temperature-catalog")
    val writer = S3LayerWriter(attributeStore)

    processSourceTiles(layerName, s3SourceTiles(bucket, prefix), attributeStore, writer)
  }

  def runLocal(layerName: String, tilesDir: String)(implicit sc: SparkContext): Unit = {
    val conf = sc.hadoopConfiguration.withInputDirectory(tilesDir)
    TemporalGeoTiffInputFormat.setTimeTag(conf, "ISO_TIME")
    TemporalGeoTiffInputFormat.setTimeFormat(conf, "yyyy-MM-dd'T'HH:mm:ss")

    val sourceTiles =
      sc.newAPIHadoopRDD(
        conf,
        classOf[TemporalGeoTiffInputFormat],
        classOf[TemporalProjectedExtent],
        classOf[Tile]
      )

    val attributeStore = FileAttributeStore(catalogPath)
    val writer = FileLayerWriter(attributeStore)

    processSourceTiles(layerName, sourceTiles, attributeStore, writer, Some(100))
  }

  def runCustomLocal(layerName: String, tilesDir: String)(implicit sc: SparkContext): Unit = {
    val conf = sc.hadoopConfiguration.withInputDirectory(tilesDir)
    TemporalGeoTiffInputFormat.setTimeTag(conf, "ISO_TIME")
    TemporalGeoTiffInputFormat.setTimeFormat(conf, "yyyy-MM-dd'T'HH:mm:ss")

    val sourceTiles =
      sc.newAPIHadoopRDD(
        conf,
        classOf[TemporalGeoTiffInputFormat],
        classOf[TemporalProjectedExtent],
        classOf[Tile]
      )

    val instance = AccumuloInstance("gis", "localhost", "root", new PasswordToken("secret"))
    val attributeStore = AccumuloAttributeStore(instance.connector)
    val writer = new AccumuloLayerWriter(attributeStore, instance, "test_custom", AccumuloLayerWriter.Options.DEFAULT)

    processSourceTiles(layerName, sourceTiles, writer.attributeStore, writer, Some(100))
  }

  def runCustomAccumulo(layerName: String, bucket: String, prefix: String)(implicit sc: SparkContext): Unit = {
    val instanceName = "geotrellis-accumulo-cluster"
    val zooKeeper = "zookeeper.service.geotrellis-spark.internal"
    val user = "root"
    val password = new PasswordToken("secret")

    val instance = AccumuloInstance(instanceName, zooKeeper, user, password)
    val attributeStore = AccumuloAttributeStore(instance.connector)
    val writer = new AccumuloLayerWriter(attributeStore, instance, "tiles", AccumuloLayerWriter.Options.DEFAULT)

    processSourceTiles(layerName, s3SourceTiles(bucket, prefix), attributeStore, writer)
  }

}
