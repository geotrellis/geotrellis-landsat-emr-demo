package demo

import geotrellis.raster._
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

object TimeGridFunction extends Function1[DateTime, Int] with Serializable {
  val startTime = new DateTime(2010, 1, 1, 0, 0)
  def apply(dt: DateTime): Int =
    Days.daysBetween(startTime, dt).getDays
}

object LandsatIngest {
  val catalogPath = "/Users/rob/proj/workshops/apple/data/landsat-catalog"

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

    val images =
      Array[String](
        "/Users/rob/proj/workshops/apple/data/flooding/batesville/LC80240352015292LGN00",
        "/Users/rob/proj/workshops/apple/data/flooding/batesville/LC80240352015324LGN00",
        "/Users/rob/proj/workshops/apple/data/philly/LC80140322013152LGN00",
        "/Users/rob/proj/workshops/apple/data/philly/LC80140322014139LGN00"
      )

    try {
      run(images, bands)
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
    // Read time
    val mtl = MTL(findMTLFile(imagePath))

    val bandTiffs =
      bands
        .map { band =>
          SingleBandGeoTiff(findBandTiff(imagePath, band))
        }

    (mtl, MultiBandGeoTiff(ArrayMultiBandTile(bandTiffs.map(_.tile)), bandTiffs.head.extent, bandTiffs.head.crs))
  }

  def run(images: Array[String], bands: Array[String])(implicit sc: SparkContext): Unit = {
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
        acc :+ rdd.reproject(targetLayoutScheme, Bilinear)
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
    val writer = FileLayerWriter[SpaceTimeKey, MultiBandTile, RasterMetaData](attributeStore, ZCurveKeyIndexMethod.by(TimeGridFunction))
    val layerName = "landsat"

    Pyramid.upLevels(rdd, targetLayoutScheme, zoom) { (rdd, zoom) =>
      writer.write(LayerId(layerName, zoom), rdd)
      if(zoom == 0) {
        // Write the temporal components as an attribute.
        attributeStore.write(LayerId(layerName, 0), "times", rdd.map(_._1.instant).collect.toArray)
      }
    }
  }
}
