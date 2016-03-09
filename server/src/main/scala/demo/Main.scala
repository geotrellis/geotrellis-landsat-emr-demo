package demo

import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.render._
import geotrellis.raster.resample._

import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.accumulo._
import geotrellis.spark.io.avro._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.json._
import geotrellis.spark.io.index._

import org.apache.spark._
import org.apache.avro.Schema

import org.apache.accumulo.core.client.security.tokens._

import com.github.nscala_time.time.Imports._
import akka.actor._
import akka.io.IO
import spray.can.Http
import spray.routing.{HttpService, RequestContext}
import spray.routing.directives.CachingDirectives
import spray.http.MediaTypes
import spray.json._
import spray.json.DefaultJsonProtocol._

import com.typesafe.config.ConfigFactory

import scala.concurrent._
import scala.collection.JavaConverters._
import scala.reflect.ClassTag

object Main {
  /** Allows an alternate execution path to run some testing */
  val TEST = false

  val dateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ")

  /** Usage:
    * First argument is catalog type. Others are dependant on the first argument.
    *
    * local CATALOG_DIR
    * s3 BUCKET_NAME CATALOG_KEY
    * accumulo INSTANCE ZOOKEEPER USER PASSWORD
    */
  def main(args: Array[String]): Unit = {
    val conf =
      new SparkConf()
        .setIfMissing("spark.master", "local[*]")
        .setAppName("Demo Server")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")

    implicit val sc = new SparkContext(conf)

    val readerSet =
      if(args(0) == "local") {
        val localCatalog = args(1)

        new FileReaderSet(localCatalog)
      } else if(args(0) == "custom-local"){
        val instanceName = "gis"
        val zooKeeper = "localhost"
        val user = "root"
        val password = new PasswordToken("secret")
        val instance = AccumuloInstance(instanceName, zooKeeper, user, password)

        new CustomAccumuloReaderSet(instance)
      } else if(args(0) == "s3"){
        val bucket = args(1)
        val prefix = args(2)

        new S3ReaderSet(bucket, prefix)
      } else if(args(0) == "accumulo") {
        val instanceName = args(1)
        val zooKeeper = args(2)
        val user = args(3)
        val password = new PasswordToken(args(4))
        val instance = AccumuloInstance(instanceName, zooKeeper, user, password)

        new AccumuloReaderSet(instance)
      } else if(args(0) == "custom-accumulo") {
        val instanceName = args(1)
        val zooKeeper = args(2)
        val user = args(3)
        val password = new PasswordToken(args(4))
        val instance = AccumuloInstance(instanceName, zooKeeper, user, password)

        new CustomAccumuloReaderSet(instance)
      } else {
        sys.error(s"Unknown catalog type ${args(0)}")
      }

    if(TEST) {
      try {
//        RunTest(readerSet)
        ???
      } finally {
        sc.stop
      }
    } else {
      implicit val system = akka.actor.ActorSystem("demo-system")

      // create and start our service actor
      val service =
        system.actorOf(Props(classOf[DemoServiceActor], readerSet, sc), "demo")

      // start a new HTTP server on port 8088 with our service actor as the handler
      IO(Http) ! Http.Bind(service, "0.0.0.0", 8088)
    }
  }
}
