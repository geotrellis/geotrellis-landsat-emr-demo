package demo

import geotrellis.vector._
import geotrellis.vector.io._

import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.index._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.file._
import geotrellis.spark.io.accumulo._
import geotrellis.spark.io.hbase._
import com.azavea.landsatutil._

import org.apache.accumulo.core.client.security.tokens._
import org.apache.commons.io.IOUtils
import org.apache.hadoop.fs._
import org.apache.hadoop.conf._
import org.apache.spark._
import spray.json._
import org.joda.time._

import java.net.URI
import java.io.File

case class Config (
  bbox: Extent,
  output: String,
  params: Map[String, String],
  maxCloudCoverage: Double = 100.0,
  startDate: LocalDate = new LocalDate(2014,1,1),
  endDate: LocalDate = new LocalDate(2015,1,1),
  layerName: String = "landsat",
  cache: Option[File] = None,
  limit: Option[Int] = None
) {

  def cacheHook(): IOHook = cache match {
    case Some(dir) => IOHook.localCache(dir)
    case None => IOHook.passthrough
  }

  def writer(implicit sc: SparkContext): LayerWriter[LayerId] = output match {
    case "s3" =>
      S3LayerWriter(params("bucket"), params("prefix"))
    case "file" =>
      FileLayerWriter(params("path"))
    case "hdfs" =>
      HadoopLayerWriter(new Path(params("path")))
    case "accumulo" =>
      val zookeeper: String = params.get("zookeeper").getOrElse {
        val conf = new Configuration // if not specified assume zookeeper is same as DFS master
        new URI(conf.get("fs.defaultFS")).getHost
      }
      val instance = AccumuloInstance(params("instance"), zookeeper, params("user"), new PasswordToken(params("password")))
      val strategy = params.get("ingestPath") match {
        case Some(path) => HdfsWriteStrategy(path)
        case None => SocketWriteStrategy()
      }
      AccumuloLayerWriter(instance, params("table"), strategy)

    case "hbase" =>
      val zookeepers: Seq[String] = params.get("zookeepers").map(_.split(",").toSeq).getOrElse {
        val conf = new Configuration // if not specified assume zookeeper is same as DFS master
        Seq(new URI(conf.get("fs.defaultFS")).getHost)
      }

      val master: String = params.get("master").getOrElse {
        val conf = new Configuration // if not specified assume zookeeper is same as DFS master
        new URI(conf.get("fs.defaultFS")).getHost
      }

      val instance = HBaseInstance(zookeepers, master)
      HBaseLayerWriter(instance, params("table"))
  }
}

object Config {
  implicit val localDateRead: scopt.Read[LocalDate] =
  scopt.Read.reads(LocalDate.parse)

  val parser = new scopt.OptionParser[Config]("scopt") {
    head("landsat import", "0.x")

    opt[String]('b', "bbox")
      .required()
      .action { (x, c) => c.copy(bbox = Extent.fromString(x)) }
      .text("xmin,ymin,xmax,ymax in LatLng")

    opt[String]('n', "layerName")
      .action { (x, c) => c.copy(layerName = x) }
      .text("Layer name to be used")

    opt[Double]('c', "maxCloudCoverage")
      .action { (x, c) => c.copy(maxCloudCoverage = x) }

    opt[LocalDate]("startDate")
      .action { (x, c) => c.copy(startDate = x) }

    opt[LocalDate]("endDate")
      .action { (x, c) => c.copy(endDate = x) }

    opt[Int]('l', "limit")
      .action { (x, c) => c.copy(limit = Some(x)) }
      .text("Limit query result to x images")

    opt[String]('o', "output")
      .required()
      .action { (x, c) => c.copy(output = x) }
      .text("output layer writer type: s3, file, accumulo")

    opt[Map[String,String]]("params")
      .required()
      .valueName("k1=v1,k2=v2...")
      .action { (x, c) => c.copy(params = x) }
      .text("parameters to configure output layer writer")

    opt[File]("cache")
      .action { (x, c) => c.copy(cache = Some(x)) }
      .validate { x =>
        if (x.exists && x.isDirectory) { success }
        else if (x.exists ){ failure(s"$x is not a directory") }
        else { x.mkdirs; success }
      }

    help("help")
      .text("prints this usage text")
  }

  def parse(args: Array[String]): Config = {
    parser.parse(args, Config(null, null, null)).getOrElse(sys.exit)
  }
}
