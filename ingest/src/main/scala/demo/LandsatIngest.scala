package demo

import com.github.nscala_time.time.Imports._
import geotrellis.vector._
import geotrellis.vector.io._
import geotrellis.vector.io.json._
import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.index._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.file._
import geotrellis.spark.io.accumulo._
import geotrellis.spark.util._
import geotrellis.spark.tiling._
import geotrellis.proj4._
import geotrellis.spark.ingest._
import com.azavea.landsatutil.{S3Client => lsS3Client, _}

import spray.json.DefaultJsonProtocol._
import org.apache.spark._
import org.apache.spark.rdd._
import org.apache.accumulo.core.client.security.tokens._
object LandsatIngest {

  // val polygon: Polygon = ???

  // val images = Landsat8Query()
  //   .withStartDate(new DateTime(2012, 1, 12, 0, 0, 0))
  //   .withEndDate(new DateTime(2015, 11, 5, 0, 0, 0))
  //   .withMaxCloudCoverage(80.0)
  //   .intersects(polygon)
  //   .collect()

  /** Accept a list of landsat image descriptors we will ingest into a geotrellis layer.
    * It is expected that the user will generate this list using `Landsat8Query` and prefilter.
    */
  def run(
    layerName: String,
    writer: LayerWriter[LayerId],
    images: Seq[LandsatImage],
    bandsWanted: Seq[String]
  )(implicit sc: SparkContext): Unit = {

    // parallelize downloading landsat images through the executo``rs
    // optionally we could have fetched MTL files and performed additional advanced filter
    // TODO: this should be a function
    val imgRdd: RDD[(TemporalProjectedExtent, MultibandTile)] = sc
      .parallelize(images, images.length)
      .mapPartitions({ iter =>
        val s3client = lsS3Client()
        for (img <- iter) yield {
          val raster = s3client.get(img, bandsWanted)
          val key = TemporalProjectedExtent(raster.extent, raster.crs, img.aquisitionDate)
          (key, raster.tile)
        }
      }, preservesPartitioning = true)

    // Our dataset can span UTM zones, we must reproject the tiles individually to common projection
    MultibandIngest[TemporalProjectedExtent, SpaceTimeKey](
      sourceTiles = imgRdd,
      destCRS = WebMercator,
      layoutScheme = ZoomedLayoutScheme(WebMercator, 256),
      resampleMethod = Bilinear,
      pyramid = true
    ){ (rdd, zoom) =>
      writer.write(LayerId(layerName, zoom), rdd, ZCurveKeyIndexMethod.byMonth() )
      if (zoom == 1) {
        // more efficient to do it when spatial dimension is smallest
        writer.attributeStore.write(
          LayerId(layerName, 0), "times",
          rdd
            .map(_._1.instant)
            .countByValue
            .keys.toArray
            .sorted)
      }
    }
  }

}

object IngestLandsatMain {
  def main(args: Array[String]): Unit = {
    implicit val sc = SparkUtils.createSparkContext("GeoTrellis Landsat Ingest", new SparkConf(true))
    val philly = GeoJson.fromFile[Polygon]("/Users/eugene/philly.json")
    val instance = AccumuloInstance("gis", "localhost", "root", new PasswordToken("secret"))
    // val writer = AccumuloLayerWriter(instance, "tiles", HdfsWriteStrategy("hdfs://localhost:9000/geotrellis-ingest"))
    // val writer = S3LayerWriter("bucket", "catalog")
    val writer = FileLayerWriter("/Users/eugene/tmp/catalog")

    try {
      val images = Landsat8Query()
        .withStartDate(new DateTime(2012, 1, 12, 0, 0, 0))
        .withEndDate(new DateTime(2015, 11, 5, 0, 0, 0))
        .withMaxCloudCoverage(80.0)
        .intersects(philly)
        .collect()
        .take(1)
      LandsatIngest.run("philadelphia", writer, images, List("1", "2", "3", "QA"))

    } finally {
      sc.stop()
    }
  }
}
