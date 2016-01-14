package demo

import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.raster.reproject._
import geotrellis.vector._
import geotrellis.raster.io.geotiff._
import geotrellis.spark._
import geotrellis.spark.ingest._
import geotrellis.spark.tiling._
import geotrellis.spark.io.file._
import geotrellis.spark.io.index._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.slippy._
import geotrellis.spark.io.json._
import geotrellis.proj4._

import org.apache.commons.io.filefilter._

import org.apache.spark._
import org.apache.spark.rdd._
import spray.json.DefaultJsonProtocol._

import com.github.nscala_time.time.Imports._
import org.joda.time.Days

import java.io._


object DebugIngest {
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
        "4", "3", "2"
      )

    // Manually set the image directories

    val images =
      Array[String](
        "/Users/rob/proj/workshops/apple/data/flooding/batesville/LC80240352015292LGN00"
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
          SingleBandGeoTiff.compressed(findBandTiff(imagePath, band))
//          SingleBandGeoTiff(findBandTiff(imagePath, band))
        }

    (mtl, MultiBandGeoTiff(ArrayMultiBandTile(bandTiffs.map(_.tile)), bandTiffs.head.extent, bandTiffs.head.crs))
  }

  // def run(images: Array[String], bands: Array[String])(implicit sc: SparkContext): Unit = {
  //   val targetLayoutScheme = ZoomedLayoutScheme(WebMercator, 256)
  //   // Create tiled RDDs for each
  //   val image = "/Users/rob/proj/workshops/apple/data/flooding/batesville/LC80240352015292LGN00"
  //   val (zoom, rdd) = {
  //     val (mtl, geoTiff) = readBands(image, bands)
  //     val ingestElement = (ProjectedExtent(geoTiff.extent, geoTiff.crs), geoTiff.tile)
  //     val sourceTile = sc.parallelize(Seq(ingestElement))
  //     val LayoutLevel(_, layoutDefinition) = FloatingLayoutScheme(512).levelFor(geoTiff.extent, geoTiff.raster.cellSize)
  //     val metadata = RasterMetaData(geoTiff.cellType, layoutDefinition, geoTiff.extent, geoTiff.crs)
  //     val tiled =
  //       sourceTile
  //         .tileToLayout[SpatialKey](metadata, Tiler.Options(resampleMethod = NearestNeighbor, partitioner = new HashPartitioner(100)))
  //         .mapValues(_.convert(TypeInt).map((b, z) => if(z < 1) NODATA else z))

  //     MultiBandRasterRDD(tiled, metadata.copy(cellType = TypeInt)).reproject(targetLayoutScheme, Bilinear)
  //   }
    
  //   val outputPath = "/Users/rob/proj/workshops/apple/data/landsat-debug"

  //   Pyramid.upLevels(rdd, targetLayoutScheme, zoom) { (rdd, z) =>
  //     val md = rdd.metadata

  //     val writer = new HadoopSlippyTileWriter[MultiBandTile](outputPath, "tif")({ (key, tile) =>
  //       val extent = md.mapTransform(key)
  //       MultiBandGeoTiff(tile, extent, WebMercator).toByteArray
  //     })
  //     writer.write(z, rdd)
  //   }
  // }

  def run(images: Array[String], bands: Array[String])(implicit sc: SparkContext): Unit = {
    val targetLayoutScheme = ZoomedLayoutScheme(WebMercator, 256)

    val image = "/Users/rob/proj/workshops/apple/data/flooding/batesville/LC80240352015292LGN00/LC80240352015292LGN00_B1.TIF"
//    val image = "/Users/rob/proj/workshops/apple/data/philly/LC80140322013152LGN00/LC80140322013152LGN00_B1.TIF"
//    val image = "/Users/rob/proj/workshops/apple/data/test-philly-np.tif"
    val gt = SingleBandGeoTiff(image)
    val raster = gt.raster
//    val raster = Raster(gt.tile.convert(TypeInt).map(z => if(z < 1) NODATA else z), gt.extent)
//    val raster = Raster(gt.tile.convert(TypeInt), gt.extent)

    // val r = gt.raster.reproject(gt.crs, WebMercator)
    // GeoTiff(r, WebMercator).write("/Users/rob/proj/workshops/apple/data/debug.tif")

    val ingestElement = (ProjectedExtent(gt.extent, gt.crs), raster.tile)
    val sourceTile = sc.parallelize(Seq(ingestElement))
    val (_, rasterMetaData) =
      RasterMetaData.fromRdd(sourceTile, FloatingLayoutScheme(512))

    val tiled =
      sourceTile
        .tileToLayout[SpatialKey](rasterMetaData, Tiler.Options(resampleMethod = NearestNeighbor, partitioner = new HashPartitioner(100)))
//        .mapValues(_.convert(TypeInt).map(z => if(z < 1) NODATA else z))

//    GeoTiff(RasterRDD(tiled, rasterMetaData).stitch.toArrayTile, rasterMetaData.extent, gt.crs).write("/Users/rob/proj/workshops/apple/data/debug-s.tif")

    val rdd = RasterRDD(tiled, rasterMetaData).reproject(targetLayoutScheme, Reproject.Options(method=Bilinear, errorThreshold=0))._2
//    val rdd = RasterRDD(tiled, rasterMetaData).reproject(targetLayoutScheme, bufferSize = 200, Reproject.Options(method=Bilinear, errorThreshold=0))._2
//    val rdd = RasterRDD(tiled, rasterMetaData.copy(cellType = TypeInt)).reproject(targetLayoutScheme, bufferSize = 10, Reproject.Options(method=Bilinear, errorThreshold=0))._2


    GeoTiff(rdd.stitch.toArrayTile, rdd.metadata.extent, WebMercator).write("/Users/rob/proj/workshops/apple/data/debug-final.tif")
  }
}
