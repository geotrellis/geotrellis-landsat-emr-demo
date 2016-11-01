package demo.etl

import geotrellis.spark.etl._
import geotrellis.spark.etl.config._
import geotrellis.spark.io.AttributeStore
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.accumulo.AccumuloAttributeStore
import geotrellis.spark.io.hbase.HBaseAttributeStore
import geotrellis.spark.io.cassandra.CassandraAttributeStore
import geotrellis.spark.io.hadoop.HadoopAttributeStore
import geotrellis.spark.io.s3.S3AttributeStore

import org.apache.hadoop.conf.Configuration
import org.apache.spark.SparkConf
import org.apache.spark.deploy.SparkHadoopUtil

import java.net.URI

package object landsat {
  implicit class withEtlConfLandsatMethods(val self: EtlConf) extends EtlConfLandsatMethods

  private[landsat] def getAttributeStore(conf: EtlConf): AttributeStore = {
    conf.output.backend.`type` match {
      case AccumuloType => {
        AccumuloAttributeStore(conf.outputProfile.collect { case ap: AccumuloProfile =>
          ap.getInstance
        }.get.connector)
      }
      case HBaseType => {
        HBaseAttributeStore(conf.outputProfile.collect { case ap: HBaseProfile =>
          ap.getInstance
        }.get)
      }
      case CassandraType => {
        CassandraAttributeStore(conf.outputProfile.collect { case cp: CassandraProfile =>
          cp.getInstance
        }.get)
      }
      case HadoopType | FileType =>
        HadoopAttributeStore(hadoop.getPath(conf.output.backend).path, SparkHadoopUtil.get.newConfiguration(new SparkConf()))
      case S3Type => {
        val path = s3.getPath(conf.output.backend)
        S3AttributeStore(path.bucket, path.prefix)
      }
      case UserDefinedBackendType(s) => throw new Exception(s"No Attribute store for user defined backend type $s")
      case UserDefinedBackendInputType(s) => throw new Exception(s"No Attribute store for user defined backend input type $s")
    }
  }

  def confWithDefaults(conf: EtlConf) = {
    def getDefaultFS = {
      val conf = new Configuration // if not specified assume zookeeper is same as DFS master
      new URI(conf.get("fs.defaultFS")).getHost
    }

    conf.output.backend.`type` match {
      case AccumuloType =>
        new EtlConf(
          input  = conf.input,
          output = conf.output.copy(
            backend = conf.output.backend.copy(
              profile = conf.output.backend.profile.map {
                case ap: AccumuloProfile => if(ap.zookeepers.isEmpty) ap.copy(zookeepers = getDefaultFS) else ap
                case p => p
              }
            )
          )
        )
      case CassandraType =>
        new EtlConf(
          input  = conf.input,
          output = conf.output.copy(
            backend = conf.output.backend.copy(
              profile = conf.output.backend.profile.map {
                case ap: CassandraProfile => if(ap.hosts.isEmpty) ap.copy(hosts = getDefaultFS) else ap
                case p => p
              }
            )
          )
        )
      case HBaseType =>
        new EtlConf(
          input  = conf.input,
          output = conf.output.copy(
            backend = conf.output.backend.copy(
              profile = conf.output.backend.profile.map {
                case ap: HBaseProfile => {
                  val nap = if (ap.zookeepers.isEmpty) ap.copy(zookeepers = getDefaultFS) else ap
                  if(ap.master.isEmpty) nap.copy(master = getDefaultFS) else nap
                }
                case p => p
              }
            )
          )
        )
      case _ => conf
    }
  }

  def getPath(b: Backend): UserDefinedPath = {
    b.path match {
      case p: UserDefinedPath => p
      case _ => throw new Exception("Path string not corresponds backend type")
    }
  }
}
