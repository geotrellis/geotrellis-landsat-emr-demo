package demo

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import geotrellis.raster.resample._
import geotrellis.raster.rasterize._
import geotrellis.raster.histogram._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.tiling._
import geotrellis.vector._
import geotrellis.vector.io._
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
import spray.json.DefaultJsonProtocol._

import com.github.nscala_time.time.Imports._
import com.typesafe.config.ConfigFactory
import com.github.nscala_time.time.Imports._
import scala.concurrent._
import spire.syntax.cfor._

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
    pathPrefix("maxstate") { maxStateRoute } ~
    pathPrefix("maxaveragestate") { maxAverageStateRoute } ~
    pathPrefix("layerdiff") { layerDiffRoute } ~
    pathPrefix("statediff") { stateDiffRoute } ~
    pathPrefix("state") { stateRoute }

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
                /** TODO problem here is that we're need to list all metadata, but we don't know what each layer key is */

                val metadata = metadataReader.read(LayerId(name, 0)) // bug ?
                  val extent =
                    metadata.rasterMetaData.extent.reproject(metadata.rasterMetaData.crs, LatLng)
                  val times =
                    metadata.times
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
                    "isLandsat" -> JsBoolean(isLandsat(name))
                  )
                }.toJson
            )
          }
        }
      }
    }

  /** Find the breaks for one layer */
  def tilesRoute =
    path("breaks" / Segment) { (layerName) =>
      import spray.json.DefaultJsonProtocol._
      parameters('time, 'operation ?) { (timeString, operationOpt) =>

        val time = DateTime.parse(timeString, dateTimeFormat)
        cors {
          complete {
            future {
              val zooms = metadataReader.layerNamesToZooms(layerName)
              val mid = zooms(zooms.length / 2)

              metadataReader.readLayerAttribute[Array[Int]](layerName, "breaks")
            }
          }
        }
      }
    } ~
    pathPrefix(Segment / IntNumber / IntNumber / IntNumber) { (layer, zoom, x, y) =>
      parameters('time, 'breaks ?, 'operation ?) { (timeString, breaksStrOpt, operationOpt) =>
        val time = DateTime.parse(timeString, dateTimeFormat)
        respondWithMediaType(MediaTypes.`image/png`) {
          complete {
            future {
              if(isLandsat(layer)) {
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

                  png.bytes
                }
              } else {
                readerSet.readSinglebandTile(layer, zoom, x, y, time)
                  .map { tile =>
                    val breaks = breaksStrOpt.get.split(',').map(_.toInt)
                    Render.temperature(tile, breaks).bytes
                  }
              }
            }
          }
        }
      }
    }


  def diffRoute =
    path("breaks" / Segment) { (layerName) =>
      import spray.json.DefaultJsonProtocol._
      cors {
        get {
          parameters('time1, 'time2) { (time1Str, time2Str) =>
            complete {
              future {
                val zooms = metadataReader.layerNamesToZooms(layerName)
                val mid = zooms(zooms.length / 2)
                val time1 = DateTime.parse(time1Str, dateTimeFormat)
                val time2 = DateTime.parse(time2Str, dateTimeFormat)

                readerSet.singleBandLayerReader
                  .query[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](LayerId(layerName, mid))
                  .where(Between(time1, time1))
                  .result
                  .withContext { rdd =>
                    rdd.union(
                      readerSet.singleBandLayerReader
                        .query[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](LayerId(layerName, mid))
                        .where(Between(time2, time2))
                        .result
                    )
                  }.map { case (key, tile) => (key.spatialKey, (key, tile)) }
                  .reduceByKey { case ((key1, tile1), (key2, tile2)) =>
                    val tile =
                      if(key1.instant == time1.instant) { tile1 - tile2 }
                      else { tile2 - tile1 }

                    (key1, tile)
                  }
                  .map { case (_, (_, tile)) => tile.histogram }
                  .reduce { (h1, h2) => h1 merge h2 }
                  .quantileBreaks(15)
              }
            }
          }
        }
      }
    } ~
    pathPrefix(Segment / IntNumber / IntNumber / IntNumber) { (layer, zoom, x, y) =>
      parameters('time1, 'time2, 'breaks ?, 'operation ?) { (timeString1, timeString2, breaksStrOpt, operationOpt) =>
        val time1 = DateTime.parse(timeString1, dateTimeFormat)
        val time2 = DateTime.parse(timeString2, dateTimeFormat)
        respondWithMediaType(MediaTypes.`image/png`) {
          complete {
            future {
              if(isLandsat(layer)) {
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
              } else {
                val tileOpt1 =
                  readerSet.readSinglebandTile(layer, zoom, x, y, time1)

                val tileOpt2 =
                  tileOpt1.flatMap { tile1 =>
                    readerSet.readSinglebandTile(layer, zoom, x, y, time2).map { tile2 => (tile1, tile2) }
                  }

                tileOpt2.map { case (tile1, tile2) =>
                  val breaks = breaksStrOpt.get.split(',').map(_.toInt)
                  val diffTile = tile1 - tile2
                  Render.temperatureDiff(diffTile, breaks).bytes
                }
              }
            }
          }
        }
      }
    }

  def layerDiffRoute =
    path("breaks" / Segment / Segment) { (layerName1, layerName2) =>
      import spray.json.DefaultJsonProtocol._
      cors {
        get {
          parameters('time1, 'time2) { (time1Str, time2Str) =>
            complete {
              future {
                val zooms = metadataReader.layerNamesToZooms(layerName1)
                val mid = zooms(zooms.length / 2)
                val time1 = DateTime.parse(time1Str, dateTimeFormat)
                val time2 = DateTime.parse(time2Str, dateTimeFormat)

                readerSet.singleBandLayerReader
                  .query[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](LayerId(layerName1, mid))
                  .where(Between(time1, time1))
                  .result
                  .withContext { rdd =>
                    rdd.union(
                      readerSet.singleBandLayerReader
                        .query[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](LayerId(layerName2, mid))
                        .where(Between(time2, time2))
                        .result
                    )
                  }
                  .map { case (key, tile) => (key.spatialKey, (key, tile)) }
                  .reduceByKey { case ((key1, tile1), (key2, tile2)) =>
                    val tile =
                      if(key1.instant == time1.instant) { tile1 - tile2 }
                      else { tile2 - tile1 }

                    (key1, tile)
                  }
                  .map { case (_, (_, tile)) => tile.histogram }
                  .reduce { (h1, h2) => h1 merge h2 }
                  .quantileBreaks(15)
              }
            }
          }
        }
      }
    } ~
    pathPrefix(Segment / Segment / IntNumber / IntNumber / IntNumber) { (layer1, layer2, zoom, x, y) =>
      parameters('time1, 'time2, 'breaks) { (timeString1, timeString2, breaksStr) =>
        val time1 = DateTime.parse(timeString1, dateTimeFormat)
        val time2 = DateTime.parse(timeString2, dateTimeFormat)
        val breaks = breaksStr.split(',').map(_.toInt)
        respondWithMediaType(MediaTypes.`image/png`) {
          complete {
            future {
              val tileOpt1 =
                readerSet.readSinglebandTile(layer1, zoom, x, y, time1)

              val tileOpt2 =
                tileOpt1.flatMap { tile1 =>
                  readerSet.readSinglebandTile(layer2, zoom, x, y, time2).map { tile2 => (tile1, tile2) }
                }

              tileOpt2.map { case (tile1, tile2) =>
                val diffTile = tile1 - tile2
                Render.temperatureDiff(diffTile, breaks).bytes
              }
            }
          }
        }
      }
    }


  val orderedStates =
    States.load()
      .sortBy(_.data.name)
      .toArray

  val reprojectedStates =
    orderedStates
      .map(_.mapGeom(_.reproject(LatLng, WebMercator)))

  val bcReprojectedStates =
    sc.broadcast(reprojectedStates)

  val statesByName = reprojectedStates.map { stateFeature => (stateFeature.data.name, stateFeature) }.toMap

  def maxStateRoute =
    path(Segment / Segment) { (layer1Name, layer2Name) =>
      import spray.json.DefaultJsonProtocol._
      cors {
        get {
          parameters('time1, 'time2, 'operation ?) { (time1Str, time2Str, operationOpt) =>
            complete {
              future {
                if(isLandsat(layer2Name)) {
                  None
                } else {
                  val zoom = metadataReader.layerNamesToMaxZooms(layer1Name) - 1
                  val time1 = DateTime.parse(time1Str, dateTimeFormat)
                  val time2 = DateTime.parse(time2Str, dateTimeFormat)
                  val stateLength = orderedStates.length

                  val bcStates = bcReprojectedStates

                  val maxValues =
                    readerSet.singleBandLayerReader
                      .query[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](LayerId(layer1Name, zoom))
                      .where(Between(time1, time1))
                      .result
                      .withContext { rdd =>
                        rdd.union(
                          readerSet.singleBandLayerReader
                            .query[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](LayerId(layer2Name, zoom))
                            .where(Between(time2, time2))
                            .result
                        )
                      }.withContext { rdd =>
                        rdd
                          .map { case (key, tile) => (key.spatialKey, (key, tile)) }
                          .reduceByKey { case ((key1, tile1), (key2, tile2)) =>
                            val tile =
                              tile1.combine(tile2) { (t1, t2) =>
                                if(isData(t1) && isData(t2)) {
                                  math.abs(t1 - t2)
                                } else {
                                  NODATA
                                }
                              }

                            (key1, tile)
                          }
                          .map(_._2)
                      }
                      .asRasters
                      .map { case (_, raster) =>
                        val reprojectedStates = bcStates.value
                        val results = Array.ofDim[Int](stateLength)
                        cfor(0)(_ < stateLength, _ + 1) { i =>
                          var max = Int.MinValue
                          raster.rasterExtent.foreach(reprojectedStates(i).geom) { (col, row) =>
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
                        val results = Array.ofDim[Int](stateLength)
                        cfor(0)(_ < stateLength, _ + 1) { i =>
                          results(i) = math.max(arr1(i), arr2(i))
                        }
                        results
                      }

                  var max = Int.MinValue
                  var maxIndex = -1
                  cfor(0)(_ < stateLength, _ + 1) { i =>
                    val stateMax = maxValues(i)
                    if(stateMax > max) {
                      maxIndex = i
                      max = stateMax
                    }
                  }

                  val state = orderedStates(maxIndex)

                  val data =
                    JsObject(
                      "name" -> JsString(state.data.name),
                      "Maximum Temperature" -> JsNumber(max)
                    )

                  Some(state.mapData { d => data })
                }
              }
            }
          }
        }
      }
    }

  def maxAverageStateRoute =
    path(Segment / Segment) { (layer1Name, layer2Name) =>
      import spray.json.DefaultJsonProtocol._
      cors {
        get {
          parameters('time1, 'time2, 'operation ?) { (time1Str, time2Str, operationOpt) =>
            complete {
              future {
                if(isLandsat(layer1Name)) {
                  None
                } else {
                  val zoom = metadataReader.layerNamesToMaxZooms(layer1Name) - 1
                  val time1 = DateTime.parse(time1Str, dateTimeFormat)
                  val time2 = DateTime.parse(time2Str, dateTimeFormat)
                  val stateLength = orderedStates.length

                  val bcStates = bcReprojectedStates

                  val (sums, counts) =
                    readerSet.singleBandLayerReader
                      .query[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](LayerId(layer1Name, zoom))
                      .where(Between(time1, time1))
                      .result
                      .withContext { rdd =>
                        rdd.union(
                          readerSet.singleBandLayerReader
                            .query[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](LayerId(layer2Name, zoom))
                            .where(Between(time2, time2))
                            .result
                        )
                      }.withContext { rdd =>
                        rdd
                          .map { case (key, tile) => (key.spatialKey, (key, tile)) }
                          .reduceByKey { case ((key1, tile1), (key2, tile2)) =>
                            val tile =
                              tile1.combine(tile2) { (t1, t2) =>
                                if(isData(t1) && isData(t2)) {
                                  math.abs(t1 - t2)
                                } else {
                                  NODATA
                                }
                              }

                            (key1, tile)
                          }
                          .map(_._2)
                      }
                      .asRasters
                      .map { case (_, raster) =>
                        val reprojectedStates = bcStates.value
                        val (sums, counts) = (Array.ofDim[Int](stateLength), Array.ofDim[Int](stateLength))
                        cfor(0)(_ < stateLength, _ + 1) { i =>
                          var sum = 0
                          var count = 0
                          raster.rasterExtent.foreach(reprojectedStates(i).geom) { (col, row) =>
                            val z = raster.tile.get(col, row)
                            if(isData(z)) {
                              sum += math.abs(z)
                              count += 1
                            }
                          }
                          sums(i) = sum
                          counts(i) = count
                        }
                        (sums, counts)
                      }
                      .reduce { (tup1, tup2) =>
                        val (sums1, counts1) = tup1
                        val (sums2, counts2) = tup2
                        val (sums, counts) = (Array.ofDim[Int](stateLength), Array.ofDim[Int](stateLength))
                        cfor(0)(_ < stateLength, _ + 1) { i =>
                          sums(i) = sums1(i) + sums2(i)
                          counts(i) = counts1(i) + counts2(i)
                        }
                        (sums, counts)
                      }

                  var maxMean = -1000.0
                  var maxIndex = -1
                  val means = Array.ofDim[Double](stateLength)
                  cfor(0)(_ < stateLength, _ + 1) { i =>
                    val mean = sums(i).toDouble / counts(i)
                    if(mean > maxMean) {
                      maxMean = mean
                      maxIndex = i
                    }
                  }

                  val state = orderedStates(maxIndex)

                  val data =
                    JsObject(
                      "name" -> JsString(state.data.name),
                      "Mean Temperature" -> JsNumber(f"${maxMean}%.2f".toDouble)
                    )

                  Some(state.mapData { d => data })
                }
              }
            }
          }
        }
      }
    }

  def stateRoute =
    pathPrefix("tiles" / Segment / Segment / IntNumber / IntNumber / IntNumber) { (stateName, layer, zoom, x, y) =>
      parameters('time, 'breaks) { (timeString, breaksStr) =>
        val time = DateTime.parse(timeString, dateTimeFormat)
        respondWithMediaType(MediaTypes.`image/png`) {
          complete {
            future {
              val state = statesByName(stateName)
              val metadata = metadataReader.read(LayerId(layer, zoom))
              val extent =
                    metadata.rasterMetaData.mapTransform(SpatialKey(x, y))
              readerSet.readSinglebandTile(layer, zoom, x, y, time)
                .map { tile =>
                  val masked =
                    tile.mask(extent, state.geom)
                  Render.temperature(masked, breaksStr.split(',').map(_.toInt)).bytes
                }
            }
          }
        }
      }
    } ~
    pathPrefix("average" / Segment / Segment) { (stateName, layer) =>
      import spray.json.DefaultJsonProtocol._
      cors {
        parameters('time) { (timeString) =>
          val time = DateTime.parse(timeString, dateTimeFormat)
          complete {
            future {
              val state = statesByName(stateName)
              val zoom = metadataReader.layerNamesToMaxZooms(layer) - 1

              val mean =
                readerSet.singleBandLayerReader
                  .query[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](LayerId(layer, zoom))
                  .where(Between(time, time))
                  .where(Intersects(state.geom))
                  .result
                  .polygonalMean(state.geom)
              state
                .mapGeom(geom => geom.reproject(WebMercator, LatLng))
                .mapData { d =>
                  JsObject(
                    "name" -> JsString(d.name),
                    "meanTemp" -> JsNumber(f"${mean}%.2f".toDouble)
                  )
                }
            }
          }
        }
      }
    }

  def stateDiffRoute =
    path("breaks" / Segment / Segment / Segment) { (stateName, layerName1, layerName2) =>
      import spray.json.DefaultJsonProtocol._
      cors {
        get {
          parameters('time1, 'time2) { (time1Str, time2Str) =>
            complete {
              future {
                val state = statesByName(stateName)
                val zooms = metadataReader.layerNamesToZooms(layerName1)
                val mid = zooms(zooms.length / 2)
                val time1 = DateTime.parse(time1Str, dateTimeFormat)
                val time2 = DateTime.parse(time2Str, dateTimeFormat)

                readerSet.singleBandLayerReader
                  .query[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](LayerId(layerName1, mid))
                  .where(Between(time1, time1))
                  .where(Intersects(state.geom))
                  .result
                  .withContext { rdd =>
                    rdd.union(
                      readerSet.singleBandLayerReader
                        .query[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](LayerId(layerName2, mid))
                        .where(Between(time2, time2))
                        .where(Intersects(state.geom))
                        .result
                    )
                  }
                  .map { case (key, tile) => (key.spatialKey, (key, tile)) }
                  .reduceByKey { case ((key1, tile1), (key2, tile2)) =>
                    val tile =
                      if(key1.instant == time1.getMillis) { tile1 - tile2 }
                      else { tile2 - tile1 }

                    (key1, tile)
                  }
                  .map { case (_, (_, tile)) => tile.histogram }
                  .reduce { (h1, h2) => h1 merge h2 }
                  .quantileBreaks(15)
              }
            }
          }
        }
      }
    } ~
    pathPrefix("tiles" / Segment / Segment / Segment / IntNumber / IntNumber / IntNumber) { (stateName, layer1, layer2, zoom, x, y) =>
      parameters('time1, 'time2, 'breaks) { (timeString1, timeString2, breaksStr) =>
        val time1 = DateTime.parse(timeString1, dateTimeFormat)
        val time2 = DateTime.parse(timeString2, dateTimeFormat)
        val breaks = breaksStr.split(',').map(_.toInt)
        val state = statesByName(stateName)
        val metadata1 = metadataReader.read(LayerId(layer1, zoom))
        val extent1 =
          metadata1.rasterMetaData.mapTransform(SpatialKey(x, y))

        val metadata2 = metadataReader.read(LayerId(layer2, zoom))
        val extent2 =
          metadata2.rasterMetaData.mapTransform(SpatialKey(x, y))

        respondWithMediaType(MediaTypes.`image/png`) {
          complete {
            future {
              val tileOpt1 =
                readerSet.readSinglebandTile(layer1, zoom, x, y, time1)

              val tileOpt2 =
                tileOpt1.flatMap { tile1 =>
                  readerSet.readSinglebandTile(layer2, zoom, x, y, time2).map { tile2 => (tile1, tile2) }
                }

              tileOpt2.map { case (tile1, tile2) =>
                val diffTile = tile1.mask(extent1, state.geom) - tile2.mask(extent2, state.geom)
                Render.temperatureDiff(diffTile, breaks).bytes
              }
            }
          }
        }
      }
    } ~
    pathPrefix("average" / Segment / Segment /Segment) { (stateName, layer1, layer2) =>
      import spray.json.DefaultJsonProtocol._
      cors {
        parameters('time1, 'time2) { (time1String, time2String) =>
          val time1 = DateTime.parse(time1String, dateTimeFormat)
          val time2 = DateTime.parse(time2String, dateTimeFormat)
          complete {
            future {
              val state = statesByName(stateName)
              val zoom = metadataReader.layerNamesToMaxZooms(layer1) - 1

              val mean =
                readerSet.singleBandLayerReader
                  .query[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](LayerId(layer1, zoom))
                  .where(Between(time1, time1))
                  .where(Intersects(state.geom))
                  .result
                  .withContext { rdd =>
                    rdd.union(
                      readerSet.singleBandLayerReader
                        .query[SpaceTimeKey, Tile, TileLayerMetadata[SpaceTimeKey]](LayerId(layer2, zoom))
                        .where(Between(time2, time2))
                        .where(Intersects(state.geom))
                        .result
                    )
                    .map { case (key, tile) => (key.spatialKey, (key, tile)) }
                    .groupByKey
                    .mapValues { tiles =>
                      val Seq((key1, tile1), (key2, tile2)) = tiles
                      val tile =
                        if(key1.instant == time1.getMillis) { tile1 - tile2 }
                        else { tile2 - tile1 }

                      tile
                    }
                  }
                  .mapContext{ md => md.copy(bounds = for (b <- md.bounds) yield KeyBounds(b.minKey.spatialKey, b.maxKey.spatialKey)) }
                  .polygonalMean(state)

              state
                .mapGeom(geom => geom.reproject(WebMercator, LatLng))
                .mapData { d =>
                  JsObject(
                    "name" -> JsString(d.name),
                    "meanTemp" -> JsNumber(f"${mean}%.2f".toDouble)
                  )
                }
            }
          }
        }
      }
    }
}
