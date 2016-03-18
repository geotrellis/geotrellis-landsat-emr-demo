package demo

import geotrellis.raster._
import geotrellis.raster.reproject._
import geotrellis.raster.resample._
import geotrellis.raster.io.geotiff._
import geotrellis.spark._
import geotrellis.spark.ingest._
import geotrellis.spark.tiling._
import geotrellis.spark.io.file._
import geotrellis.spark.io.index._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io._
import geotrellis.proj4._

import org.apache.commons.io.filefilter._

import org.apache.spark._
import org.apache.spark.rdd._
import spray.json.DefaultJsonProtocol._

import com.github.nscala_time.time.Imports._
import org.joda.time.Days

import java.io._

object PlanetLabsIngest {
  val catalogPath = "/Volumes/Transcend/data/workshop/jan2016/data/landsat-catalog"

  def main(args: Array[String]): Unit = {
    // Setup Spark to use Kryo serializer.
    val conf =
      new SparkConf()
        .setIfMissing("spark.master", "local[4]")
        .setAppName("PlanetLabs Ingest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")

    implicit val sc = new SparkContext(conf)


    val sfImages =
      Array[(String, DateTime)](
        // Currently working out a bug in LZW compression reading discovered by this image in compressed form
        // Manually set time (can parse from the file name)
        ("/Volumes/Transcend/data/workshop/jan2016/data/pl/20151216_214011_0c68_visual-uncompressed.tif", new DateTime(2015, 12, 16, 21, 40, 11)),
        ("/Volumes/Transcend/data/workshop/jan2016/data/pl/20150904_214546_0b0e_visual-uncompressed.tif", new DateTime(2015, 9, 4, 21, 45, 46))
      )


    try {
      run("GoldenGatePark-planet-landsat", sfImages)

      // Pause to wait to close the spark context,
      // so that you can check out the UI at http://localhost:4040
      println("Hit enter to exit.")
      readLine()
    } finally {
      sc.stop()
    }
  }

  def run(layerName: String, images: Array[(String, DateTime)])(implicit sc: SparkContext): Unit = {
    val targetLayoutScheme = ZoomedLayoutScheme(WebMercator, 256)
    // Create tiled RDDs for each
    val tileSets =
      images.foldLeft(Seq[(Int, MultibandTileLayerRDD[SpaceTimeKey])]()) { case (acc, (path, time)) =>
        val geoTiff = MultibandGeoTiff(path)
        val ingestElement = (TemporalProjectedExtent(geoTiff.extent, geoTiff.crs, time), geoTiff.tile)
        val sourceTile = sc.parallelize(Seq(ingestElement))
        val (_, metadata) =
          TileLayerMetadata.fromRdd[TemporalProjectedExtent, MultibandTile, SpaceTimeKey](sourceTile, FloatingLayoutScheme(512))

        val tiled =
          sourceTile
            .tileToLayout[SpaceTimeKey](metadata, Tiler.Options(resampleMethod = NearestNeighbor, partitioner = new HashPartitioner(100)))

        val rdd = MultibandTileLayerRDD(tiled, metadata)

        // Reproject to WebMercator
        acc :+ rdd.reproject(targetLayoutScheme, bufferSize = 30, Reproject.Options(method = Bilinear, errorThreshold = 0))
      }

    val zoom = tileSets.head._1
    val rdd = ContextRDD(
      sc.union(tileSets.map(_._2)),
      tileSets.map({ case (_, rdd) => rdd.metadata}).reduce({ _ merge _ }) // These are all of the same zoom level
    )

    // Write to the catalog
    val attributeStore = FileAttributeStore(catalogPath)
    val writer = FileLayerWriter(attributeStore)
    val method = ZCurveKeyIndexMethod.byDay

    val lastRdd =
      Pyramid.upLevels(rdd, targetLayoutScheme, zoom, Bilinear) { (rdd, zoom) =>
        writer.write(LayerId(layerName, zoom), rdd, method)
      }

    attributeStore.write(LayerId(layerName, 0), "times", lastRdd.map(_._1.instant).collect.toArray)
  }
}
