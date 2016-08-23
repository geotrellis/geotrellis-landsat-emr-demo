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
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import com.github.nscala_time.time.Imports._

import scala.concurrent._
import spire.syntax.cfor._

import scala.util.Try

class DemoServiceActor(
  readerSet: ReaderSet,
  sc: SparkContext
) extends Actor with HttpService with CORSSupport {
  import scala.concurrent.ExecutionContext.Implicits.global

  val dateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ")
  val metadataReader = readerSet.metadataReader
  val attributeStore = readerSet.attributeStore

  def timedCreate[T](f: => T): (T, String) = {
    val s = System.currentTimeMillis
    val result = f
    val e = System.currentTimeMillis
    val t = "%,d".format(e - s)
    result -> t
  }


  def isLandsat(name: String) =
    name.contains("landsat")

  def actorRefFactory = context
  def receive = runRoute(root)

  def root =
    path("ping") { complete { "pong\n" } } ~
      path("catalog") { catalogRoute }  ~
      pathPrefix("tiles") { tilesRoute } ~
      pathPrefix("diff") { diffRoute } ~
      pathPrefix("mean") { polygonalMeanRoute } ~
      pathPrefix("series") { timeseriesRoute } ~
      pathPrefix("readall") { readallRoute }

  def timeseriesRoute = {
    import spray.json.DefaultJsonProtocol._

    pathPrefix(Segment / Segment) { (layer, op) =>
      parameters('lat, 'lng, 'zoom ?) { (lat, lng, zoomLevel) =>
        cors {
          complete {
            future {
              val zoom = zoomLevel
                .map(_.toInt)
                .getOrElse(metadataReader.layerNamesToMaxZooms(layer))

              val catalog = readerSet.layerReader
              val layerId = LayerId(layer, zoom)
              val point = Point(lng.toDouble, lat.toDouble).reproject(LatLng, WebMercator)

              // Wasteful but safe
              val fn = op match {
                case "ndvi" => NDVI.apply(_)
                case "ndwi" => NDWI.apply(_)
                case _ => sys.error(s"UNKNOWN OPERATION")
              }

              val rdd = catalog.query[SpaceTimeKey, MultibandTile, TileLayerMetadata[SpaceTimeKey]](layerId)
                .where(Intersects(point.envelope))
                .result

              val mt = rdd.metadata.mapTransform

              val answer = rdd
                .map { case (k, tile) =>
                  // reconstruct tile raster extent so we can map the point to the tile cell
                  val re = RasterExtent(mt(k), tile.cols, tile.rows)
                  val (tileCol, tileRow) = re.mapToGrid(point)
                  val ret = fn(tile).getDouble(tileCol, tileRow)
                  println(s"$point equals $ret at ($tileCol, $tileRow) in tile $re ")
                  (k.time, ret)
                }
                .collect
                .filterNot(_._2.isNaN)
                .toJson

              JsObject("answer" -> answer)
            }
          }
        }
      }
    }
  }

  def polygonalMeanRoute = {
    import spray.json.DefaultJsonProtocol._

    pathPrefix(Segment / Segment) { (layer, op) =>
      parameters('time, 'otherTime ?, 'zoom ?) { (time, otherTime, zoomLevel) =>
        cors {
          post {
            entity(as[String]) { json =>
              complete {
                future {
                  val zoom = zoomLevel
                    .map(_.toInt)
                    .getOrElse(metadataReader.layerNamesToMaxZooms(layer))

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

                  val rdd1 = catalog
                    .query[SpaceTimeKey, MultibandTile, TileLayerMetadata[SpaceTimeKey]](layerId)
                    .where(At(DateTime.parse(time, dateTimeFormat)))
                    .where(Intersects(extent))
                    .result
                  val answer1 = ContextRDD(rdd1.mapValues({ v => fn(v) }), rdd1.metadata).polygonalMean(geometry)

                  val answer2: Double = otherTime match {
                    case None => 0.0
                    case Some(otherTime) =>
                      val rdd2 = catalog
                        .query[SpaceTimeKey, MultibandTile, TileLayerMetadata[SpaceTimeKey]](layerId)
                        .where(At(DateTime.parse(otherTime, dateTimeFormat)))
                        .where(Intersects(extent))
                        .result

                      ContextRDD(rdd2.mapValues({ v => fn(v) }), rdd2.metadata).polygonalMean(geometry)
                  }

                  val answer = answer1 - answer2

                  JsObject("answer" -> JsNumber(answer))
                }
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
                  val (extent, crs) = Try {
                    attributeStore.read[(Extent, CRS)](LayerId(name, 0), "extent")
                  }.getOrElse((LatLng.worldExtent, LatLng))

                  extent.reproject(crs, LatLng)
                }

                val times = attributeStore.read[Array[Long]](LayerId(name, 0), "times")
                  .map { instant =>
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

  def readallRoute = {
    import spray.json.DefaultJsonProtocol._

    pathPrefix(Segment / IntNumber) { (layer, zoom) =>
      get {
        cors {
          complete {
            future {
              val catalog = readerSet.layerReader
              val ccatalog = readerSet.layerCReader
              val id = LayerId(layer, zoom)

              val (objrdd, strrdd) = timedCreate(
                catalog
                  .query[SpaceTimeKey, MultibandTile, TileLayerMetadata[SpaceTimeKey]](id)
                  .result.count()
              )

              val (objc, strc) = timedCreate(
                ccatalog
                  .query[SpaceTimeKey, MultibandTile, TileLayerMetadata[SpaceTimeKey]](id)
                  .result.length
              )

              JsObject(
                "obj_rdd" -> objrdd.toJson,
                "time_rdd" -> strrdd.toJson,
                "obj_collection" -> objc.toJson,
                "time_collection" -> strc.toJson,
                "conf" -> ConfigFactory.load().getObject("geotrellis").render(ConfigRenderOptions.concise()).toJson
              )
            }
          }
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
                  case Some(op) =>
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
                    case Some(op) =>
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
