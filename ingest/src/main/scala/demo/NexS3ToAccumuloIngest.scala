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
import org.apache.avro.Schema
import org.apache.commons.io.filefilter._

import org.apache.spark._
import org.apache.spark.rdd._
import spray.json.DefaultJsonProtocol._

import com.github.nscala_time.time.Imports._
import org.joda.time.Days

import java.io.File

object NEXS3ToAccumuloIngest {
  val targetLayoutScheme = ZoomedLayoutScheme(WebMercator, 256)

  def main(args: Array[String]): Unit = {
    // Setup Spark to use Kryo serializer.
    val conf =
      new SparkConf()
        .setAppName("NEX S3 to Accumulo Ingest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.hadoop.KryoRegistrator")

    implicit val sc = new SparkContext(conf)

    try {
      val attributeStore = S3AttributeStore("geotrellis-climate-catalogs", "ensemble-temperature-catalog")
      val singleBandReader = S3LayerReader[SpaceTimeKey, Tile, RasterMetaData](attributeStore)
      val multiBandReader = S3LayerReader[SpaceTimeKey, MultiBandTile, RasterMetaData](attributeStore)
      val seed = (Seq[(LayerId, RasterRDD[SpaceTimeKey])](), Seq[(LayerId, MultiBandRasterRDD[SpaceTimeKey])]())
      val layerIds =
        attributeStore.layerIds
          .groupBy(_.name)
          .map { case (name, ids) => ids.maxBy(_.zoom) }


      val (singleBands, multiBands) =
        layerIds.foldLeft(seed) { (acc, layerId) =>

          if(layerId.name.contains("landsat")) {
            (acc._1, acc._2 :+ ((layerId, multiBandReader.read(layerId))))
          } else {
            (acc._1 :+ ((layerId, singleBandReader(layerId))), acc._2)
          }
        }

      val instanceName = "geotrellis-accumulo-cluster"
      val zooKeeper = "zookeeper.service.geotrellis-spark.internal"
      val user = "root"
      val password = new PasswordToken("secret")

      val indexMethod = ZCurveKeyIndexMethod.byMillisecondResolution(1000L * 60 * 60 * 24 * 30)

      val instance = AccumuloInstance(instanceName, zooKeeper, user, password)
      val singleBandWriter = AccumuloLayerWriter[SpaceTimeKey, Tile, RasterMetaData](instance, "tiles", indexMethod)
      val multiBandWriter = AccumuloLayerWriter[SpaceTimeKey, MultiBandTile, RasterMetaData](instance, "multiband-tiles", indexMethod)

      for((layerId, singleBandRDD) <- singleBands) {
        val LayerId(layerName, zoom) = layerId
        val halfZoom = zoom / 2

        val lastRdd =
          Pyramid.upLevels(singleBandRDD, targetLayoutScheme, zoom, Bilinear) { (rdd, zoom) =>
            singleBandWriter.write(LayerId(layerName, zoom), rdd)
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

      for((layerId, multiBandRDD) <- multiBands) {
        val LayerId(layerName, zoom) = layerId

        val lastRdd =
          Pyramid.upLevels(multiBandRDD, targetLayoutScheme, zoom, Bilinear) { (rdd, zoom) =>
            multiBandWriter.write(LayerId(layerName, zoom), rdd)
          }

        attributeStore.write(LayerId(layerName, 0), "times", lastRdd.map(_._1.instant).collect.toArray)
      }

    } finally {
      sc.stop()
    }
  }
}
