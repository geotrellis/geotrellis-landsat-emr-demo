package demo

import geotrellis.raster._
import geotrellis.raster.op.local._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import geotrellis.raster.resample._
import geotrellis.raster.op.stats._
import geotrellis.raster.histogram._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.op.stats._
import geotrellis.spark.tiling._
import geotrellis.vector._
import geotrellis.vector.reproject._
import geotrellis.proj4._

import org.apache.spark._

import akka.actor._
import akka.io.IO
import spray.can.Http
import spray.routing._
import spray.routing.directives.CachingDirectives
import spray.http.MediaTypes
import spray.http.HttpHeaders.RawHeader
import spray.httpx.SprayJsonSupport._
import spray.json._

import com.github.nscala_time.time.Imports._
import com.typesafe.config.ConfigFactory

import scala.concurrent._
import spire.syntax.cfor._

object RunTest {
  def apply(readerSet: ReaderSet)(implicit sc: SparkContext): Unit = {
    val time = DateTime.parse("2056-01-16T13:00:00-0400")

    val layer =
      readerSet.singleBandLayerReader
        .query(LayerId("Climate_CCSM4-RCP45-Temperature-Max", 8))
        .where(Between(time, time))
        .toRDD

    val orderedStates =
      States.load()
        .sortBy(_.data.name)
        .toArray

    val reprojectedStates =
      orderedStates
        .map(_.mapGeom(_.reproject(LatLng, WebMercator)))

    val maxValues =
      layer
        .asRasters
        .map { case (_, raster) =>
          val results = Array.ofDim[Int](reprojectedStates.length)
          cfor(0)(_ < reprojectedStates.length, _ + 1) { i =>
            var max = Int.MinValue
            reprojectedStates(i).geom.foreachCell(raster) { (col, row) =>
              val z = raster.tile.get(col, row)
              if(isData(z)) {
                if(z > max) max = z
              }
            }
            results(i) = max
          }
          results
        }
        .reduce { (arr1, arr2) =>
          val results = Array.ofDim[Int](reprojectedStates.length)
          cfor(0)(_ < arr1.length, _ + 1) { i =>
            results(i) = math.max(arr1(i), arr2(i))
          }
          results
        }

    var max = Int.MinValue
    var maxIndex = -1
    cfor(0)(_ < reprojectedStates.length, _ + 1) { i =>
      val stateMax = maxValues(i)
      println(s" ${reprojectedStates(i).data.name} = ${stateMax}")
      if(stateMax > max) {
        maxIndex = i
        max = stateMax
      }
    }

    val maxState = orderedStates(maxIndex)
    println(s"Max state: ${maxState.data.name}, value: ${max}")
  }
}
