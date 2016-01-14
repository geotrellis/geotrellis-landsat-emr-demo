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
import geotrellis.vector._

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

class DemoServiceActor(
  layerReader: FilteringLayerReader[LayerId, SpaceTimeKey, RasterMetaData, MultiBandRasterRDD[SpaceTimeKey]],
  tileReader: CachingTileReader[SpaceTimeKey, MultiBandTile],
  metadataReader: MetadataReader,
  sc: SparkContext
) extends Actor with HttpService {
  import scala.concurrent.ExecutionContext.Implicits.global

  val dateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ")

  def isLandsat(name: String) =
    name == "landsat"

  def cors: Directive0 = respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*"))

  def actorRefFactory = context
  def receive = runRoute(root)

  def root =
    path("ping") { complete { "pong\n" } } ~
    path("catalog") { catalogRoute }  ~
    pathPrefix("tiles") { tilesRoute } ~
    pathPrefix("diff") { diffRoute }

  def catalogRoute =
    get {
      import spray.json.DefaultJsonProtocol._
      complete {
        future {
          val layerInfo =
            metadataReader.layerNamesToZooms
              .keys
              .map { name =>
                val times =
                  metadataReader.read(LayerId(name, 0)).times
                    .map { instant => dateTimeFormat.print(new DateTime(instant)) }
                (name, times)
              }


          JsObject(
            "layers" ->
              layerInfo.map { li =>
                JsObject(
                  "name" -> JsString(li._1),
                  "times" -> li._2.toJson,
                  "isLandsat" -> JsBoolean(isLandsat(li._1))
                )
              }.toJson
          )
        }
      }
    }

val redBreaks = Array(6097, 6199, 6274, 6328, 6364, 6396, 6427, 6445, 6467, 6485, 6504, 6520, 6536, 6551, 6563, 6576, 6588, 6601, 6613, 6624, 6635, 6644, 6654, 6664, 6675, 6685, 6694, 6704, 6713, 6723, 6731, 6740, 6749, 6757, 6764, 6772, 6780, 6788, 6797, 6805, 6813, 6821, 6829, 6835, 6842, 6850, 6858, 6864, 6871, 6877, 6884, 6892, 6899, 6905, 6913, 6920, 6928, 6935, 6943, 6950, 6956, 6964, 6971, 6979, 6985, 6993, 6999, 7005, 7012, 7019, 7027, 7035, 7042, 7049, 7056, 7063, 7069, 7077, 7085, 7092, 7099, 7106, 7114, 7121, 7130, 7138, 7147, 7155, 7165, 7174, 7183, 7192, 7201, 7210, 7219, 7229, 7239, 7250, 7260, 7270, 7281, 7290, 7300, 7310, 7321, 7331, 7341, 7351, 7361, 7371, 7381, 7393, 7405, 7416, 7425, 7437, 7448, 7459, 7469, 7480, 7491, 7502, 7514, 7526, 7538, 7551, 7561, 7572, 7586, 7597, 7610, 7623, 7636, 7647, 7659, 7672, 7685, 7699, 7711, 7723, 7735, 7747, 7760, 7776, 7789, 7803, 7815, 7830, 7842, 7854, 7868, 7883, 7898, 7910, 7926, 7940, 7956, 7971, 7986, 8001, 8018, 8032, 8050, 8065, 8080, 8097, 8114, 8132, 8149, 8165, 8182, 8197, 8216, 8234, 8251, 8269, 8287, 8304, 8323, 8343, 8362, 8382, 8400, 8418, 8439, 8459, 8479, 8500, 8520, 8541, 8564, 8585, 8608, 8631, 8657, 8685, 8709, 8735, 8759, 8789, 8821, 8851, 8883, 8915, 8948, 8981, 9015, 9047, 9084, 9115, 9155, 9190, 9226, 9254, 9284, 9309, 9337, 9367, 9393, 9420, 9459, 9501, 9547, 9599, 9648, 9704, 9767, 9836, 9908, 9979, 10061, 10144, 10234, 10328, 10418, 10533, 10657, 10759, 10881, 11000, 11130, 11265, 11418, 11642, 11838, 12036, 12307, 12579, 12911, 13283, 13771, 14430, 15296, 17737, 24252, 44935)

val blueBreaks = Array(6584, 6638, 6671, 6700, 6721, 6738, 6753, 6768, 6782, 6797, 6809, 6819, 6830, 6841, 6853, 6865, 6876, 6888, 6898, 6911, 6922, 6934, 6946, 6959, 6971, 6984, 6999, 7012, 7024, 7038, 7052, 7068, 7083, 7094, 7109, 7121, 7132, 7143, 7153, 7163, 7171, 7180, 7188, 7196, 7205, 7214, 7222, 7230, 7237, 7245, 7252, 7260, 7267, 7275, 7283, 7291, 7298, 7306, 7313, 7322, 7330, 7338, 7346, 7355, 7364, 7373, 7383, 7394, 7404, 7414, 7425, 7437, 7447, 7461, 7476, 7492, 7507, 7522, 7536, 7552, 7567, 7587, 7604, 7622, 7640, 7658, 7676, 7696, 7714, 7734, 7754, 7774, 7795, 7816, 7836, 7859, 7883, 7906, 7934, 7959, 7984, 8009, 8034, 8062, 8090, 8118, 8145, 8173, 8196, 8218, 8240, 8260, 8281, 8302, 8320, 8337, 8354, 8372, 8388, 8404, 8418, 8432, 8447, 8463, 8478, 8493, 8505, 8519, 8532, 8545, 8559, 8574, 8587, 8600, 8613, 8626, 8641, 8655, 8669, 8683, 8695, 8708, 8723, 8737, 8749, 8763, 8776, 8788, 8800, 8812, 8824, 8835, 8847, 8859, 8871, 8882, 8894, 8906, 8918, 8929, 8940, 8952, 8963, 8974, 8985, 8997, 9009, 9021, 9031, 9042, 9056, 9066, 9077, 9091, 9103, 9115, 9126, 9138, 9151, 9163, 9175, 9186, 9199, 9212, 9226, 9239, 9251, 9265, 9277, 9291, 9307, 9322, 9339, 9352, 9366, 9381, 9398, 9413, 9429, 9446, 9465, 9480, 9499, 9519, 9536, 9556, 9577, 9598, 9618, 9644, 9669, 9692, 9718, 9742, 9769, 9796, 9823, 9849, 9874, 9897, 9922, 9942, 9965, 9992, 10022, 10051, 10084, 10118, 10156, 10199, 10234, 10272, 10322, 10379, 10430, 10490, 10554, 10630, 10704, 10793, 10887, 10990, 11108, 11273, 11420, 11580, 11778, 11976, 12208, 12523, 12967, 13483, 14509, 17303, 23346, 42741)

val greenBreaks = Array(7382, 7421, 7449, 7468, 7483, 7497, 7510, 7521, 7531, 7541, 7551, 7559, 7567, 7576, 7584, 7592, 7599, 7608, 7616, 7625, 7633, 7640, 7649, 7656, 7664, 7673, 7682, 7691, 7700, 7710, 7720, 7732, 7742, 7754, 7767, 7778, 7793, 7806, 7818, 7831, 7843, 7853, 7864, 7873, 7880, 7887, 7896, 7903, 7909, 7916, 7922, 7928, 7935, 7941, 7946, 7951, 7956, 7962, 7967, 7973, 7979, 7985, 7990, 7996, 8002, 8008, 8014, 8020, 8025, 8031, 8037, 8044, 8051, 8059, 8068, 8076, 8082, 8092, 8101, 8112, 8122, 8133, 8145, 8160, 8175, 8190, 8207, 8221, 8239, 8256, 8273, 8296, 8314, 8336, 8355, 8374, 8398, 8419, 8443, 8467, 8492, 8517, 8544, 8570, 8602, 8632, 8665, 8705, 8734, 8762, 8785, 8806, 8825, 8840, 8857, 8876, 8891, 8905, 8916, 8931, 8943, 8954, 8964, 8976, 8986, 8995, 9005, 9014, 9022, 9031, 9040, 9048, 9056, 9065, 9074, 9083, 9092, 9101, 9109, 9118, 9126, 9135, 9142, 9153, 9162, 9171, 9179, 9187, 9196, 9205, 9214, 9223, 9233, 9242, 9251, 9261, 9270, 9281, 9293, 9303, 9314, 9325, 9335, 9347, 9358, 9368, 9380, 9389, 9401, 9412, 9424, 9433, 9444, 9455, 9466, 9478, 9491, 9505, 9519, 9533, 9545, 9561, 9575, 9588, 9602, 9618, 9632, 9646, 9662, 9677, 9694, 9709, 9726, 9744, 9761, 9779, 9798, 9818, 9837, 9855, 9873, 9893, 9914, 9937, 9958, 9981, 10004, 10029, 10052, 10077, 10103, 10128, 10156, 10182, 10206, 10239, 10270, 10300, 10332, 10364, 10399, 10432, 10463, 10493, 10532, 10570, 10606, 10642, 10686, 10731, 10789, 10840, 10896, 10962, 11023, 11069, 11113, 11155, 11190, 11224, 11261, 11310, 11364, 11433, 11505, 11618, 11737, 11914, 12115, 12354, 12750, 13447, 14580, 17490, 23607, 46176)

val totalBreaks = Array(6256, 6386, 6458, 6511, 6553, 6587, 6617, 6642, 6665, 6688, 6709, 6727, 6744, 6760, 6775, 6791, 6807, 6820, 6833, 6846, 6860, 6872, 6885, 6898, 6911, 6925, 6939, 6952, 6966, 6980, 6994, 7007, 7021, 7035, 7049, 7064, 7078, 7092, 7107, 7120, 7135, 7149, 7163, 7177, 7189, 7203, 7216, 7229, 7242, 7255, 7268, 7281, 7294, 7306, 7320, 7333, 7346, 7359, 7373, 7387, 7402, 7416, 7429, 7443, 7457, 7471, 7484, 7497, 7510, 7523, 7535, 7547, 7558, 7569, 7581, 7593, 7604, 7615, 7627, 7639, 7650, 7661, 7673, 7687, 7699, 7711, 7724, 7737, 7751, 7767, 7782, 7797, 7812, 7827, 7841, 7855, 7869, 7881, 7894, 7906, 7917, 7929, 7939, 7950, 7960, 7970, 7981, 7992, 8003, 8014, 8025, 8036, 8048, 8062, 8075, 8088, 8103, 8119, 8135, 8153, 8171, 8189, 8206, 8225, 8244, 8262, 8281, 8300, 8320, 8339, 8357, 8376, 8395, 8413, 8430, 8449, 8470, 8489, 8506, 8525, 8544, 8564, 8583, 8603, 8623, 8643, 8665, 8687, 8708, 8729, 8748, 8768, 8787, 8806, 8824, 8840, 8857, 8874, 8891, 8907, 8922, 8938, 8952, 8966, 8980, 8994, 9008, 9020, 9033, 9046, 9059, 9072, 9086, 9099, 9112, 9125, 9138, 9153, 9166, 9179, 9191, 9206, 9220, 9234, 9247, 9261, 9275, 9291, 9306, 9321, 9337, 9352, 9367, 9383, 9398, 9414, 9430, 9447, 9465, 9482, 9502, 9523, 9542, 9564, 9585, 9607, 9629, 9654, 9678, 9703, 9729, 9755, 9784, 9812, 9842, 9869, 9897, 9926, 9955, 9985, 10019, 10053, 10089, 10127, 10169, 10208, 10251, 10295, 10345, 10394, 10445, 10498, 10558, 10620, 10686, 10758, 10834, 10918, 11009, 11090, 11167, 11240, 11327, 11427, 11562, 11721, 11912, 12125, 12376, 12726, 13186, 13866, 14925, 17499, 23739, 46176)
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

              val layer =
                layerReader
                  .query(LayerId(layerName, mid))
                  .where(Between(time, time))
                  .toRDD

              if(isLandsat(layerName)) {
                // Find the min/max
                // val (min, max) =
                // layerReader
                //   .read(LayerId(layerName, mid))
                //   .map { case (_, tile) =>
                //     var min = Int.MaxValue
                //     var max = Int.MinValue

                //     tile.combine(0, 1, 2) { (r, g, b) =>
                //       min = math.min(min, if(r > 0) r else Int.MaxValue)
                //       min = math.min(min, if(g > 0) g else Int.MaxValue)
                //       min = math.min(min, if(b > 0) b else Int.MaxValue)

                //       max = math.max(max, if(r > 0) r else Int.MinValue)
                //       max = math.max(max, if(g > 0) g else Int.MinValue)
                //       max = math.max(max, if(b > 0) b else Int.MinValue)
                //       0
                //     }
                //     (min, max)
                //   }
                //   .reduce { (a, b) =>
                //     (math.min(a._1, b._2), math.max(a._2, b._2))
                //   }

                //   Array(min, max)

                // val (r, g, b) =
                // layerReader
                //   .read(LayerId(layerName, mid))
                //   .map { case (_, tile) =>
                //     val red = tile.band(0).convert(TypeInt).map(z => if(z == 0) NODATA else z).histogram
                //     val green = tile.band(1).convert(TypeInt).map(z => if(z == 0) NODATA else z).histogram
                //     val blue = tile.band(2).convert(TypeInt).map(z => if(z == 0) NODATA else z).histogram
                //     (red, green, blue)
                //   }
                //   .reduce { (a, b) =>
                //     val (r1, b1, g1) = a
                //     val (r2, b2, g2) = b
                //     (
                //       FastMapHistogram.fromHistograms(Seq(r1, r2)),
                //       FastMapHistogram.fromHistograms(Seq(b1, b2)),
                //       FastMapHistogram.fromHistograms(Seq(g1, g2))
                //     )
                //   }
                // Array(r.getQuantileBreaks(256), g.getQuantileBreaks(256), b.getQuantileBreaks(256))

                operationOpt match {
                  case Some(operation) =>
                    operation match {
                      case "ndvi" =>
                        layer
                          .withContext(_.mapValues { tile => NDVI(tile) * 1000 })
                          .classBreaks(15)
                      case "ndwi" =>
                        layer
                          .withContext(_.mapValues { tile => NDWI(tile) * 1000 })
                          .classBreaks(15)
                      case _ =>
                        sys.error(s"UNKNOWN OPERATION $operation")
                    }
                  case None =>
                    val histo =
                      layer
                        .map { case (_, tile) =>
                          val red = tile.band(0).convert(TypeInt).map(z => if(z == 0) NODATA else z).histogram
                          val green = tile.band(1).convert(TypeInt).map(z => if(z == 0) NODATA else z).histogram
                          val blue = tile.band(2).convert(TypeInt).map(z => if(z == 0) NODATA else z).histogram
                          FastMapHistogram.fromHistograms(Seq(red, green, blue))
                        }
                        .reduce { (a, b) =>
                          FastMapHistogram.fromHistograms(Seq(a, b))
                        }
                    histo.getQuantileBreaks(256)
                }
              } else {
                layer
                  .withContext { rdd => rdd.mapValues(_.band(1)) }
                  .classBreaks(15)
              }
            }
          }
        }
      }
    } ~
    pathPrefix(Segment / IntNumber / IntNumber / IntNumber) { (layer, zoom, x, y) =>
      parameters('time, 'breaks, 'operation ?) { (timeString, breaksStr, operationOpt) =>
        val time = DateTime.parse(timeString, dateTimeFormat)
        respondWithMediaType(MediaTypes.`image/png`) {
          complete {
            future {
              val tileOpt =
                try {
                  Some(tileReader.read(LayerId(layer, zoom), SpaceTimeKey(x, y, time)))
                } catch {
                  case e: TileNotFoundError =>
                    None
                }

              tileOpt.map { tile =>
                val breaks = breaksStr.split(',').map(_.toInt)
                if(isLandsat(layer)) {
                  val png =
                    operationOpt match {
                      case Some (op) =>
                        op match {
                          case "ndvi" =>
                            Render.ndvi(tile, breaks)
                          case "ndwi" =>
                            Render.ndwi(tile, breaks)
                          case _ =>
                            sys.error(s"UNKNOWN OPERATION $op")
                        }
                      case None =>
                        Render.image(tile, breaks)
                    }

                  png.bytes
                } else {
                  ???
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
      get {
        cors {
          parameters('time1, 'time2, 'operation ?) { (time1Str, time2Str, operationOpt) =>
            complete {
              future {
                if(isLandsat(layerName)) {
                  val zooms = metadataReader.layerNamesToZooms(layerName)
                  val mid = zooms(zooms.length / 2)
                  val time1 = DateTime.parse(time1Str, dateTimeFormat)
                  val time2 = DateTime.parse(time2Str, dateTimeFormat)

                  val rdd =
                    layerReader
                      .query(LayerId(layerName, mid))
                      .where(Between(time1, time1) or Between(time2, time2))
                      .toRDD

                  operationOpt match {
                    case Some(op) =>
                      rdd
                        .mapValues { tile =>
                        op match {
                          case "ndvi" => NDVI(tile) * 1000.0
                          case "ndwi" => NDWI(tile) * 1000.0
                          case _ => sys.error(s"UNKNOWN OPERATION $op")
                        }
                      }
                        .reduceByKey(_ - _)
                        .map { case (_, tile) => tile.histogram }
                        .reduce { (h1, h2) => FastMapHistogram.fromHistograms(Array(h1, h2)) }
                        .getQuantileBreaks(15)
                    case None =>
                      rdd
                        .reduceByKey { (tile1, tile2) =>
                        // How do I do this diff?
                        ???
                      }
                      ???
                  }
                } else {
                  ???
                }
              }
            }
          }
        }
      }
    } ~
    pathPrefix(Segment / IntNumber / IntNumber / IntNumber) { (layer, zoom, x, y) =>
      parameters('time1, 'time2, 'breaks, 'operation ?) { (timeString1, timeString2, breaksStr, operationOpt) =>
        val time1 = DateTime.parse(timeString1, dateTimeFormat)
        val time2 = DateTime.parse(timeString2, dateTimeFormat)
        respondWithMediaType(MediaTypes.`image/png`) {
          complete {
            future {
              val tileOpt1 =
                try {
                  Some(tileReader.read(LayerId(layer, zoom), SpaceTimeKey(x, y, time1)))
                } catch {
                  case e: TileNotFoundError =>
                    None
                }

              val tileOpt2 =
                tileOpt1.flatMap { tile =>
                  try {
                    Some((tile, tileReader.read(LayerId(layer, zoom), SpaceTimeKey(x, y, time1))))
                  } catch {
                    case e: TileNotFoundError =>
                      None
                  }
                }

              tileOpt2.map { case (tile1, tile2) =>
                val breaks = breaksStr.split(',').map(_.toInt)
                if(isLandsat(layer)) {
                  val png =
                    operationOpt match {
                      case Some (op) =>
                        op match {
                          case "ndvi" =>
                            Render.ndvi(tile1, tile2, breaks)
                          case "ndwi" =>
                            Render.ndwi(tile1, tile2, breaks)
                          case _ =>
                            sys.error(s"UNKNOWN OPERATION $op")
                        }
                      case None =>
                        ???
                    }

                  png.bytes
                } else {
                  ???
                }
              }
            }
          }
        }
      }
    }
}
