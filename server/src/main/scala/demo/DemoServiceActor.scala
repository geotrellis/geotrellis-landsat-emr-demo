package demo

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.histogram._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.rasterize._
import geotrellis.raster.render._
import geotrellis.raster.resample._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.raster.summary.polygonal._
import geotrellis.spark.tiling._
import geotrellis.vector._
import geotrellis.vector.io._
import geotrellis.vector.reproject._

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
import com.github.nscala_time.time.Imports._
import scala.concurrent._
import spire.syntax.cfor._

import scala.util.Try

class DemoServiceActor(
  readerSet: ReaderSet,
  sc: SparkContext
) extends Actor with HttpService {
  import scala.concurrent.ExecutionContext.Implicits.global

  val dateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ")

  val metadataReader = readerSet.metadataReader
  val attributeStore = readerSet.attributeStore

  def isLandsat(name: String) =
    name.contains("landsat")

  def cors: Directive0 = respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*"))

  def actorRefFactory = context
  def receive = runRoute(root)

  def root =
    path("ping") { complete { "pong\n" } } ~
    path("catalog") { catalogRoute }  ~
    pathPrefix("tiles") { tilesRoute } ~
    pathPrefix("diff") { diffRoute } ~
    pathPrefix("mean") { polygonalMeanRoute } ~
    pathPrefix("series") { timeseriesRoute }

  def timeseriesRoute = {
    import spray.json.DefaultJsonProtocol._

    pathPrefix(Segment / IntNumber / Segment) { (layer, zoom, op) =>
      parameters('lat, 'lng) { (lat, lng) =>
        complete {
          val catalog = readerSet.layerReader
          val layerId = LayerId(layer, zoom)

          val geometry = Point(lng.toDouble, lat.toDouble).reproject(LatLng, WebMercator)
          val extent = geometry.envelope

          // Wasteful but safe
          val fn = op match {
            case "ndvi" => NDVI.apply(_)
            case "ndwi" => NDWI.apply(_)
            case _ => sys.error(s"UNKNOWN OPERATION")
          }

          val rdd = catalog.query[SpaceTimeKey, MultibandTile, TileLayerMetadata[SpaceTimeKey]](layerId)
            .where(Intersects(extent))
            .result
          val mt = rdd.metadata.mapTransform

          val answer = rdd.map({ case (k, v) =>
            val re = RasterExtent(mt(k), v.cols, v.rows)
            var retval: Double = 0.0

            Rasterizer.foreachCellByGeometry(geometry, re)({ (col,row) =>
              val tile = fn(v)
              retval = tile.getDouble(col, row)
            })
            (k.time, retval)
          })
            .collect
            .toJson

          JsObject("answer" -> answer)
        }
      }
    }
  }

  def polygonalMeanRoute = {
    import spray.json.DefaultJsonProtocol._

    pathPrefix(Segment / IntNumber / Segment) { (layer, zoom, op) =>
      parameters('time, 'otherTime ?) { (time, otherTime) =>
        cors {
          post {
            entity(as[String]) { json =>
              complete {
                val catalog = readerSet.layerReader
                val layerId = LayerId(layer, zoom)

                val rawGeometry = try {
                  json.parseJson.convertTo[Geometry]
                } catch {
                  case e: Exception => sys.error("THAT PROBABLY WASN'T GEOMETRY")
                }
                val geometry = rawGeometry match {
                  case p: Polygon => MultiPolygon(p.reproject(LatLng, WebMercator))
                  case mp: MultiPolygon => mp.reproject(LatLng, WebMercator)
                  case _ => sys.error(s"BAD GEOMETRY")
                }
                val extent = geometry.envelope

                val fn = op match {
                  case "ndvi" => NDVI.apply(_)
                  case "ndwi" => NDWI.apply(_)
                  case _ => sys.error(s"UNKNOWN OPERATION")
                }

                val rdd = catalog
                  .query[SpaceTimeKey, MultibandTile, TileLayerMetadata[SpaceTimeKey]](layerId)
                  .where(At(DateTime.parse(time, dateTimeFormat)))
                  .where(Intersects(extent))
                  .result
                val md = rdd.metadata

                val answer: Double = otherTime match {
                  case None =>
                    // The metadata are not correct here, but that is
                    // okay in this instance because the polygonalMean
                    // method does not need them to be.
                    ContextRDD(rdd.mapValues({ v => fn(v) }), md).polygonalMean(geometry)
                  case Some(otherTime) =>
                    val rdd2 = catalog
                      .query[SpaceTimeKey, MultibandTile, TileLayerMetadata[SpaceTimeKey]](layerId)
                      .where(At(DateTime.parse(otherTime, dateTimeFormat)))
                      .where(Intersects(extent))
                      .result
                      .map({ case (k,v) => (k.spatialKey, fn(v)) })
                    val rdd3 = rdd.map({ case (k,v) => (k.spatialKey, fn(v)) })
                      .join(rdd2).map({ case (k, (v1, v2)) =>
                        (k, v1.combineDouble(v2)({ (a, b) => a - b }))
                      })

                    val cellType = rdd3.first._2.cellType
                    val stBounds = md.bounds.asInstanceOf[KeyBounds[SpaceTimeKey]]
                    val bounds = KeyBounds(stBounds.minKey.spatialKey, stBounds.maxKey.spatialKey)
                    val metadata = TileLayerMetadata(cellType, md.layout, md.extent, md.crs, bounds)

                    // Ditto note about the metadata
                    ContextRDD(rdd3, metadata).polygonalMean(geometry)
                }

                JsObject("answer" -> JsNumber(answer))
              }
            }
          }
        }
      }
    }
  }

  /** Return a JSON representation of the catalog */
  def catalogRoute =
    cors {
      get {
        import spray.json.DefaultJsonProtocol._
        complete {
          future {
            val layerInfo =
              metadataReader.layerNamesToZooms //Map[String, Array[Int]]
                .keys
                .toList
                .sorted
                .map { name =>
                  // assemble catalog from metadata common to all zoom levels
                  val extent = {
                    val (extent, crs) = Try{
                      attributeStore.read[(Extent, CRS)](LayerId(name, 0), "extent")
                    }.getOrElse((LatLng.worldExtent, LatLng))

                    extent.reproject(crs, LatLng)
                  }

                  val times = attributeStore.read[Array[Long]](LayerId(name, 0), "times")
                    .map{ instant =>
                      dateTimeFormat.print(new DateTime(instant, DateTimeZone.forOffsetHours(-4)))
                    }
                  (name, extent, times.sorted)
                }


            JsObject(
              "layers" ->
                layerInfo.map { li =>
                  val (name, extent, times) = li
                  JsObject(
                    "name" -> JsString(name),
                    "extent" -> JsArray(Vector(Vector(extent.xmin, extent.ymin).toJson, Vector(extent.xmax, extent.ymax).toJson)),
                    "times" -> times.toJson,
                    "isLandsat" -> JsBoolean(true)
                  )
                }.toJson
            )
          }
        }
      }
    }

  /** Find the breaks for one layer */
  def tilesRoute =
    pathPrefix(Segment / IntNumber / IntNumber / IntNumber) { (layer, zoom, x, y) =>
      parameters('time, 'operation ?) { (timeString, operationOpt) =>
        val time = DateTime.parse(timeString, dateTimeFormat)
        println(layer, zoom, x, y, time)
        respondWithMediaType(MediaTypes.`image/png`) {
          complete {
            future {
              val tileOpt =
                readerSet.readMultibandTile(layer, zoom, x, y, time)

              tileOpt.map { tile =>
                val png =
                  operationOpt match {
                    case Some (op) =>
                      op match {
                        case "ndvi" =>
                          Render.ndvi(tile)
                        case "ndwi" =>
                          Render.ndwi(tile)
                        case _ =>
                          sys.error(s"UNKNOWN OPERATION $op")
                      }
                    case None =>
                      Render.image(tile)
                  }
                println(s"BYTES: ${png.bytes.length}")
                png.bytes
              }
            }
          }
        }
      }
    }

  def diffRoute =
    pathPrefix(Segment / IntNumber / IntNumber / IntNumber) { (layer, zoom, x, y) =>
      parameters('time1, 'time2, 'breaks ?, 'operation ?) { (timeString1, timeString2, breaksStrOpt, operationOpt) =>
        val time1 = DateTime.parse(timeString1, dateTimeFormat)
        val time2 = DateTime.parse(timeString2, dateTimeFormat)
        respondWithMediaType(MediaTypes.`image/png`) {
          complete {
            future {
              val tileOpt1 =
                readerSet.readMultibandTile(layer, zoom, x, y, time1)

              val tileOpt2 =
                tileOpt1.flatMap { tile1 =>
                  readerSet.readMultibandTile(layer, zoom, x, y, time2).map { tile2 => (tile1, tile2) }
                }

              tileOpt2.map { case (tile1, tile2) =>
                val png =
                  operationOpt match {
                    case Some (op) =>
                    op match {
                      case "ndvi" =>
                      Render.ndvi(tile1, tile2)
                      case "ndwi" =>
                      Render.ndwi(tile1, tile2)
                      case _ =>
                      sys.error(s"UNKNOWN OPERATION $op")
                    }
                    case None =>
                    ???
                  }

                png.bytes
              }
            }
          }
        }
      }
    }
}
