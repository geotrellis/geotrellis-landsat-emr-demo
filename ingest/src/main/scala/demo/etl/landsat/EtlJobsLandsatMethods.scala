package demo.etl.landsat

import geotrellis.spark.etl.EtlJob
import com.azavea.landsatutil.IOHook
import java.io.File

trait EtlJobsLandsatMethods {
  val self: EtlJob

  val help = """
               |geotrellis-etl-landsat-input
               |
               |Usage: geotrellis-etl-landsat-input [options]
               |
               |  --bandsWanted <value>
               |        bandsWanted is a non-empty String property
               |  --startDate <value>
               |        startDate is a non-empty String property
               |  --endDate <value>
               |        endDate is a non-empty String property
               |  --maxCloudCoverage <value>
               |        maxCloudCoverage is a non-empty String property
               | --bbox <value>
               |        bbox is a non-empty String property
               | --cache <value>
               |        cache is a non-empty String property
               | --limit <value>
               |        limit is a non-empty String property
               |  --help
               |        prints this usage text
             """.stripMargin

  def nextOption(map: Map[Symbol, String], list: Seq[String]): Map[Symbol, String] =
    list.toList match {
      case Nil => map
      case "--bandsWanted" :: value :: tail =>
        nextOption(map ++ Map('bandsWanted -> value), tail)
      case "--startDate" :: value :: tail =>
        nextOption(map ++ Map('startDate -> value), tail)
      case "--endDate" :: value :: tail =>
        nextOption(map ++ Map('endDate -> value), tail)
      case "--maxCloudCoverage" :: value :: tail =>
        nextOption(map ++ Map('maxCloudCoverage -> value), tail)
      case "--bbox" :: value :: tail =>
        nextOption(map ++ Map('bbox -> value), tail)
      case "--limit" :: value :: tail =>
        nextOption(map ++ Map('limit -> value), tail)
      case "--cache" :: value :: tail =>
        nextOption(map ++ Map('cache -> value), tail)
      case "--help" :: tail => {
        println(help)
        sys.exit(1)
      }
      case option :: tail => {
        println(s"Unknown option ${option} in landsat input string")
        println(help)
        sys.exit(1)
      }
    }

  def landsatInput = nextOption(Map(), self.input.path.split(" ").toList)

  def cacheHook: IOHook = landsatInput.get('cache).map(new File(_)) match {
    case Some(dir) => IOHook.localCache(dir)
    case None => IOHook.passthrough
  }

  def bandsWanted = landsatInput('bandsWanted).split(",")
}
