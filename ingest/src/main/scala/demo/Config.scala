package demo

import geotrellis.vector._
import geotrellis.vector.io._

import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.index._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.file._
import geotrellis.spark.io.accumulo._

import org.apache.accumulo.core.client.security.tokens._
import org.apache.commons.io.IOUtils
import org.apache.hadoop.fs._
import org.apache.hadoop.conf._
import spray.json._

import java.net.URI
import java.io.File

case class Config (
  polygonUri: URI,
  output: String,
  params: Map[String, String],
  maxCloudCoverage: Double = 100.0,
  layerName: String = "landsat",
  cache: Option[File] = None,
  limit: Option[Int] = None
) {
  def writer(): LayerWriter[LayerId] = output match {
    case "s3" =>
      S3LayerWriter(params("bucket"), params("prefix"))
    case "file" =>
      FileLayerWriter(params("path"))
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
  }

  def polygon(): Polygon = {
    val conf = new Configuration()
    val fs = FileSystem.get(polygonUri, conf)
    val is = fs.open(new Path(polygonUri))
    try {
      IOUtils.toString(is).parseJson.convertTo[Polygon]
    } finally { is.close }
  }
}

object Config {
  val parser = new scopt.OptionParser[Config]("scopt") {
    head("landsat import", "0.x")

    opt[URI]('q', "polygonUri")
      .required()
      .action { (x, c) => c.copy(polygonUri = x) }
      .text("URI of GeoJSON polygon file")

    opt[String]('n', "layerName")
      .action { (x, c) => c.copy(layerName = x) }
      .text("Layer name to be used")

    opt[Double]('c', "maxCloudCoverage")
      .action { (x, c) => c.copy(maxCloudCoverage = x) }

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
