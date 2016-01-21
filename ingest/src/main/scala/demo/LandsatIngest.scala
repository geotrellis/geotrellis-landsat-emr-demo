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
import geotrellis.spark.io.json._
import geotrellis.proj4._

import org.apache.commons.io.filefilter._

import org.apache.spark._
import org.apache.spark.rdd._
import spray.json.DefaultJsonProtocol._

import com.github.nscala_time.time.Imports._
import org.joda.time.Days

import java.io._

object LandsatIngest {
  val catalogPath = "/Volumes/Transcend/data/workshop/jan2016/data/landsat-catalog"

  def main(args: Array[String]): Unit = {
    // Setup Spark to use Kryo serializer.
    val conf =
      new SparkConf()
        .setIfMissing("spark.master", "local[4]")
        .setAppName("Landsat Ingest")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.hadoop.KryoRegistrator")

    implicit val sc = new SparkContext(conf)

    // Manually set bands
    val bands =
      Array[String](
        // Red, Green, Blue
        "4", "3", "2",
        // Near IR
        "5",
        // SWIR 1
        "6",
        // SWIR 2
        "7",
        "QA"
      )

    // Manually set the image directories

    val phillyImages =
      Array[String](
        "/Volumes/Transcend/data/workshop/jan2016/data/philly/LC80140322013152LGN00",
        "/Volumes/Transcend/data/workshop/jan2016/data/philly/LC80140322014139LGN00"
      )

    val sfImages =
      Array[String](
        "/Volumes/Transcend/data/workshop/jan2016/data/drought/LC80440342013170LGN00",
        "/Volumes/Transcend/data/workshop/jan2016/data/drought/LC80440342015176LGN00"
      )

    val vfImages =
      Array[String](
        "/Volumes/Transcend/data/workshop/jan2016/data/valleyfire/LC80450332015247LGN00",
        "/Volumes/Transcend/data/workshop/jan2016/data/valleyfire/LC80450332015263LGN00"
      )

    val batesvilleImages =
      Array[String](
        "/Volumes/Transcend/data/workshop/jan2016/data/flooding/batesville/LC80240352015292LGN00",
        "/Volumes/Transcend/data/workshop/jan2016/data/flooding/batesville/LC80240352015324LGN00"
      )

    try {
      run("Philly-landsat", phillyImages, bands)
      run("Batesville-landsat", batesvilleImages, bands)
      run("SanFrancisco-landsat", sfImages, bands)
      run("ValleyFire-landsat", vfImages, bands)

      // Pause to wait to close the spark context,
      // so that you can check out the UI at http://localhost:4040
      println("Hit enter to exit.")
      readLine()
    } finally {
      sc.stop()
    }
  }

  def fullPath(path: String) = new java.io.File(path).getAbsolutePath

  def findMTLFile(imagePath: String): String =
    new File(imagePath).listFiles(new WildcardFileFilter(s"*_MTL.txt"): FileFilter).toList match {
      case Nil => sys.error(s"MTL data not found for image at ${imagePath}")
      case List(f) => f.getAbsolutePath
      case _ => sys.error(s"Multiple files matching band MLT file found for image at ${imagePath}")
    }

  def findBandTiff(imagePath: String, band: String): String =
    new File(imagePath).listFiles(new WildcardFileFilter(s"*_B${band}.TIF"): FileFilter).toList match {
      case Nil => sys.error(s"Band ${band} not found for image at ${imagePath}")
      case List(f) => f.getAbsolutePath
      case _ => sys.error(s"Multiple files matching band ${band} found for image at ${imagePath}")
    }

  def readBands(imagePath: String, bands: Array[String]): (MTL, MultiBandGeoTiff) = {
    // Read time out of the metadata MTL file.
    val mtl = MTL(findMTLFile(imagePath))

    val bandTiffs =
      bands
        .map { band =>
          SingleBandGeoTiff(findBandTiff(imagePath, band))
        }

    (mtl, MultiBandGeoTiff(ArrayMultiBandTile(bandTiffs.map(_.tile)), bandTiffs.head.extent, bandTiffs.head.crs))
  }

  def run(layerName: String, images: Array[String], bands: Array[String])(implicit sc: SparkContext): Unit = {
    val targetLayoutScheme = ZoomedLayoutScheme(WebMercator, 256)
    // Create tiled RDDs for each
    val tileSets =
      images.foldLeft(Seq[(Int, MultiBandRasterRDD[SpaceTimeKey])]()) { (acc, image) =>
        val (mtl, geoTiff) = readBands(image, bands)
        val ingestElement = (SpaceTimeInputKey(geoTiff.extent, geoTiff.crs, mtl.dateTime), geoTiff.tile)
        val sourceTile = sc.parallelize(Seq(ingestElement))
        val (_, metadata) =
          RasterMetaData.fromRdd(sourceTile, FloatingLayoutScheme(512))

        val tiled =
          sourceTile
            .tileToLayout[SpaceTimeKey](metadata, Tiler.Options(resampleMethod = NearestNeighbor, partitioner = new HashPartitioner(100)))

        val rdd = MultiBandRasterRDD(tiled, metadata)

        // Reproject to WebMercator
        acc :+ rdd.reproject(targetLayoutScheme, bufferSize = 30, Reproject.Options(method = Bilinear, errorThreshold = 0))
      }

    val zoom = tileSets.head._1
    val headRdd = tileSets.head._2
    val rdd = ContextRDD(
      sc.union(tileSets.map(_._2)),
      RasterMetaData(
        headRdd.metadata.cellType,
        headRdd.metadata.layout,
        tileSets.map { case (_, rdd) => (rdd.metadata.extent) }.reduce(_.combine(_)),
        headRdd.metadata.crs
      )
    )

    // Write to the catalog
    val attributeStore = FileAttributeStore(catalogPath)
    val writer = FileLayerWriter[SpaceTimeKey, MultiBandTile, RasterMetaData](attributeStore, ZCurveKeyIndexMethod.byMillisecondResolution(1000L * 60 * 60 * 24))

    val lastRdd =
      Pyramid.upLevels(rdd, targetLayoutScheme, zoom, Bilinear) { (rdd, zoom) =>
        writer.write(LayerId(layerName, zoom), rdd)
      }

    attributeStore.write(LayerId(layerName, 0), "times", lastRdd.map(_._1.instant).collect.toArray)
  }
}
