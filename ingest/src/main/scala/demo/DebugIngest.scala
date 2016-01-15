package demo

import geotrellis.raster._
import geotrellis.raster.reproject.Reproject.Options
import geotrellis.raster.resample._
import geotrellis.raster.io.geotiff._
import geotrellis.spark._
import geotrellis.spark.buffer._
import geotrellis.spark.ingest._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.file._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.hadoop.formats._
import geotrellis.spark.io.index._
import geotrellis.spark.io.json._
import geotrellis.spark.io.s3._
import geotrellis.spark.op.stats._
import geotrellis.spark.tiling._
import geotrellis.proj4._
import geotrellis.vector._
import geotrellis.vector.reproject._

import org.apache.commons.io.filefilter._

import org.apache.spark._
import org.apache.spark.rdd._
import spray.json.DefaultJsonProtocol._

import com.github.nscala_time.time.Imports._
import org.joda.time.Days

import java.io._

object DebugIngest {
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

//

    try {
      "local"  match {
        case "local" =>
          runLocal("Climate_CCSM4-RCP45-Temperature-Max", "/Users/rob/proj/climate/data/fossdem/tile_export/ccsm4-rcp45")
  //        runLocal("Climate_CCSM4-RCP85-Temperature-Max", "file:///Users/rob/proj/climate/data/fossdem/tile_export/ccsm4-rcp85")
          // Pause to wait to close the spark context,
          // so that you can check out the UI at http://localhost:4040
          println("Hit enter to exit.")
          readLine()
        case "accumulo" =>
          ???
      }
    } finally {
      sc.stop()
    }
  }

  def processSourceTiles(sourceTiles: RDD[(ProjectedExtent, Tile)]): (Int, RDD[(SpatialKey, Tile)] with Metadata[RasterMetaData]) = {
    val (_, metadataWithoutCrs) =
      RasterMetaData.fromRdd(sourceTiles, FloatingLayoutScheme(512))

    val metadata = metadataWithoutCrs.copy(crs = LatLng)

    val tiled =
      sourceTiles
        .tileToLayout[SpatialKey](metadata, Tiler.Options(resampleMethod = NearestNeighbor, partitioner = new HashPartitioner(100)))

    tiled
      .bufferTiles(10)
      .collect
      .foreach { case (key, BufferedTile(tile, gridBounds)) =>
        val innerExtent = metadata.mapTransform(key)
        val innerRasterExtent = RasterExtent(innerExtent, gridBounds.width, gridBounds.height)
        val outerGridBounds =
          GridBounds(
            -gridBounds.colMin,
            -gridBounds.rowMin,
            tile.cols - gridBounds.colMin - 1,
            tile.rows - gridBounds.rowMin - 1
          )
        val outerExtent = innerRasterExtent.extentFor(outerGridBounds, clamp = false)

        val r @ Raster(newTile, newExtent) =
          tile.reproject(outerExtent, gridBounds, LatLng, WebMercator, Options(method = Bilinear, errorThreshold = 0))

        println(s"${key}")
        println(s"  ${gridBounds}")
        println(s"  ${tile.dimensions}")
        println(s"  ${outerGridBounds}")
        println(s"  ${innerExtent}")
        println(s"  ${outerExtent}")
        println(s"  ${newExtent}")

        GeoTiff(r, WebMercator).write(s"/Users/rob/tmp/workshop012016/rdd-buffertile-${key.col}-${key.row}.tif")
    }


    RasterRDD(tiled, metadata)
      .reproject(targetLayoutScheme, bufferSize = 100, Options(method = Bilinear, errorThreshold = 0))
  }

  import java.io._

  def runLocal(layerName: String, tilesDir: String)(implicit sc: SparkContext): Unit = {
    val e = Extent(-1.1427641476746991E7, 3757032.814272983, -1.1271098442818949E7, 3913575.8482010234)

    val gt1 = SingleBandGeoTiff("/Users/rob/proj/climate/data/fossdem/tile_export/ccsm4-rcp45/CCSM4-rcp45-tasmax-205601120000_3_5.tif")
    val gt2 = SingleBandGeoTiff("/Users/rob/proj/climate/data/fossdem/tile_export/ccsm4-rcp45/CCSM4-rcp45-tasmax-205601120000_4_5.tif")

    val sourceTiles =
      sc.parallelize(
        List(
          (ProjectedExtent(gt1.raster.extent, LatLng), gt1.tile),
          (ProjectedExtent(gt2.raster.extent, LatLng), gt2.tile)
        )
      )
//        processSourceTiles(sourceTiles)._2.count
    GeoTiff(processSourceTiles(sourceTiles)._2.stitch, WebMercator).write("/Users/rob/tmp/workshop012016/rdd-reproject.tif")

    // val r1 = gt1.raster.reproject(LatLng, WebMercator)
    // val r2 = gt2.raster.reproject(LatLng, WebMercator)

    // val r = Raster(ArrayTile.empty(gt1.cellType, 256, 256), e)

    // val res = r.merge(r1).merge(r2)
    // GeoTiff(r1, WebMercator).write("/Users/rob/tmp/workshop012016/raster1.tif")
    // GeoTiff(r2, WebMercator).write("/Users/rob/tmp/workshop012016/raster2.tif")
    // GeoTiff(res, WebMercator).write("/Users/rob/tmp/workshop012016/result.tif")

    // Grab the source tiles

    // val sourceTiles =
    //   sc.parallelize(new File(tilesDir).listFiles.map(_.getAbsolutePath))
    //     .flatMap { f =>
    //       val gt = SingleBandGeoTiff.compressed(f)
    //       if(gt.extent.reproject(LatLng, WebMercator).intersects(e))
    //         Some(f)
    //       else
    //         None
    //       }
    //     .collect
    //     .foreach { f => println(f) }
  }
}
