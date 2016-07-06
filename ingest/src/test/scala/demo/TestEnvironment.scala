package demo

import geotrellis.spark.testkit._
import org.apache.spark._
import org.scalatest._

trait TestEnvironment extends BeforeAndAfterAll
  with TileLayerRDDBuilders
  with TileLayerRDDMatchers
{ self: Suite with BeforeAndAfterAll =>


  def setKryoRegistrator(conf: SparkConf): Unit =
    conf
  lazy val _sc: SparkContext = {
    System.setProperty("spark.driver.port", "0")
    System.setProperty("spark.hostPort", "0")
    System.setProperty("spark.ui.enabled", "false")

    val conf = new SparkConf()
      .setMaster("local")
      .setAppName("Test Context")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")
      .set("spark.kryoserializer.buffer.max", "500m")
      .set("spark.kryo.registrationRequired","false")

    val sparkContext = new SparkContext(conf)

    System.clearProperty("spark.driver.port")
    System.clearProperty("spark.hostPort")
    System.clearProperty("spark.ui.enabled")

    sparkContext
  }

  implicit def sc: SparkContext = _sc

  // get the name of the class which mixes in this trait
  val name = this.getClass.getName

  // a hadoop configuration
  val conf = sc.hadoopConfiguration

  override def afterAll() = sc.stop()
}
