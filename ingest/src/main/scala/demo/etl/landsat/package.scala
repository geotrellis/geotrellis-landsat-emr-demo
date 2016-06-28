package demo.etl

import geotrellis.spark.etl.EtlJob
import geotrellis.spark.etl.config._
import geotrellis.spark.io.AttributeStore
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.accumulo.{AccumuloAttributeStore, AccumuloInstance}
import geotrellis.spark.io.cassandra.{BaseCassandraInstance, CassandraAttributeStore}
import geotrellis.spark.io.hadoop.HadoopAttributeStore
import geotrellis.spark.io.s3.S3AttributeStore

import org.apache.spark.SparkConf
import org.apache.spark.deploy.SparkHadoopUtil

package object landsat {
  implicit class withEtlJobsLandsatMethods(val self: EtlJob) extends EtlJobsLandsatMethods

  private[landsat] def getAttributeStore(job: EtlJob): AttributeStore = {
    job.config.ingestType.output match {
      case AccumuloType => {
        AccumuloAttributeStore(job.outputCredentials.collect { case credentials: Accumulo =>
          AccumuloInstance(
            credentials.instance,
            credentials.zookeepers,
            credentials.user,
            credentials.token
          )
        }.get.connector)
      }
      case CassandraType => {
        CassandraAttributeStore(job.outputCredentials.collect {
          case credentials: Cassandra =>
            BaseCassandraInstance(
              credentials.hosts.split(","),
              credentials.user,
              credentials.password,
              credentials.replicationStrategy,
              credentials.replicationFactor,
              credentials.localDc,
              credentials.usedHostsPerRemoteDc,
              credentials.allowRemoteDCsForLocalConsistencyLevel
            )
        }.get)
      }
      case HadoopType | FileType =>
        HadoopAttributeStore(job.outputProps("path"), SparkHadoopUtil.get.newConfiguration(new SparkConf()))
      case S3Type =>
        S3AttributeStore(job.outputProps("bucket"), job.outputProps("key"))
    }
  }
}
