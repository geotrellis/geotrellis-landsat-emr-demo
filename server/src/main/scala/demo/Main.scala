package demo

import geotrellis.spark.io.accumulo._
import geotrellis.spark.io.cassandra._
import geotrellis.spark.io.hbase._

import org.apache.spark._
import org.apache.accumulo.core.client.security.tokens._
import akka.actor._
import akka.io.IO

import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

object AkkaSystem {
  implicit val system = ActorSystem("iaas-system")
  implicit val materializer = ActorMaterializer()

  trait LoggerExecutor {
    protected implicit val log = Logging(system, "app")
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    import AkkaSystem._

    val conf: SparkConf =
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
     } else if(args(0) == "hdfs"){
        val path = new org.apache.hadoop.fs.Path(args(1))

        new HadoopReaderSet(path)
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
      } else if(args(0) == "cassandra") {
        val zooKeeper = args(1).split(",")
        val master = args(2)
        val instance = BaseCassandraInstance(zooKeeper, master)

        new CassandraReaderSet(instance)
      } else if(args(0) == "hbase") {
        val zooKeepers = args(1).split(",").toSeq
        val master = args(2)
        val instance = HBaseInstance(zooKeepers, master)

        new HBaseReaderSet(instance)
      } else {
        sys.error(s"Unknown catalog type ${args(0)}")
      }

    val router = new Router(readerSet, sc)
    Http().bindAndHandle(router.routes, "0.0.0.0", 8899)
  }
}


// object Main {
//   val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

//   /** Usage:
//     * First argument is catalog type. Others are dependant on the first argument.
//     *
//     * local CATALOG_DIR
//     * s3 BUCKET_NAME CATALOG_KEY
//     * accumulo INSTANCE ZOOKEEPER USER PASSWORD
//     */
//   def main(args: Array[String]): Unit = {

//     // create and start our service actor
//     val service =
//       system.actorOf(Props(classOf[DemoServiceActor], readerSet, sc), "demo")

//     // start a new HTTP server on port 8899 with our service actor as the handler
//     IO(Http) ! Http.Bind(service, "0.0.0.0", 8899)
//   }
// }
