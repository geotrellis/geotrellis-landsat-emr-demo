package demo

import org.scalatest._
import geotrellis.spark.testkit._

class SparkSampleSpec extends FunSpec with TestEnvironment with Matchers {
  describe("Sample spark test") {
    it("can trigger a spark job") {
      sc.parallelize(Array(1,2,3,4)).reduce(_ + _) should be (10)
    }

    it("should serialize a case class errors") {
      case class Box(x: Int)
      val b = Box(10)
      b.serializeAndDeserialize()
    }

    it("should fail to serialize some class") {
      class Box(val x: Int)
      val b = new Box(10)
      intercept[java.io.NotSerializableException] {
        b.serializeAndDeserialize()
      }
    }
  }


}
