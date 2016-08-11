package demo.etl

import geotrellis.spark.etl.config._
import geotrellis.spark.io.AttributeStore
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.accumulo.AccumuloAttributeStore
import geotrellis.spark.io.cassandra.CassandraAttributeStore
import geotrellis.spark.io.hadoop.HadoopAttributeStore
import geotrellis.spark.io.s3.S3AttributeStore

import org.apache.spark.SparkConf
import org.apache.spark.deploy.SparkHadoopUtil

package object landsat {
  implicit class withEtlConfLandsatMethods(val self: EtlConf) extends EtlConfLandsatMethods

  private[landsat] def getAttributeStore(conf: EtlConf): AttributeStore = {
    conf.output.backend.`type` match {
      case AccumuloType => {
        AccumuloAttributeStore(conf.outputProfile.collect { case ap: AccumuloProfile =>
          ap.getInstance
        }.get.connector)
      }
      case CassandraType => {
        CassandraAttributeStore(conf.outputProfile.collect { case cp: CassandraProfile =>
            cp.getInstance
        }.get)
      }
      case HadoopType | FileType =>
        HadoopAttributeStore(conf.outputProps("path"), SparkHadoopUtil.get.newConfiguration(new SparkConf()))
      case S3Type =>
        S3AttributeStore(conf.outputProps("bucket"), conf.outputProps("key"))
      case UserDefinedBackendType(s) => throw new Exception(s"No Attribute store for user defined backend type $s")
      case UserDefinedBackendInputType(s) => throw new Exception(s"No Attribute store for user defined backend input type $s")
    }
  }
}
