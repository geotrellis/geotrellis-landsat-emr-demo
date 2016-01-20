package demo

import geotrellis.raster.{MultiBandTile, Tile}
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.accumulo._
import geotrellis.spark.io.avro._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.index.KeyIndex
import geotrellis.spark.io.json._
import geotrellis.spark.utils.KryoWrapper

import org.apache.accumulo.core.client.IteratorSetting
import org.apache.accumulo.core.client.mapreduce.InputFormatBase
import org.apache.accumulo.core.data.{Range => AccumuloRange, Key, Value}
import org.apache.accumulo.core.util.{Pair => AccumuloPair}

import org.apache.avro.Schema
import org.apache.hadoop.io.Text
import org.apache.hadoop.mapreduce.Job
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.joda.time.DateTimeZone

import spray.json._
import scala.collection.JavaConversions._
import scala.reflect._

class CustomAccumuloLayerReader[V: AvroRecordCodec: ClassTag, M: JsonFormat](
  instance: AccumuloInstance
)(implicit sc: SparkContext)
  extends FilteringLayerReader[LayerId, SpaceTimeKey, M, RDD[(SpaceTimeKey, V)] with Metadata[M]] {

  val attributeStore = AccumuloAttributeStore(instance.connector)
  val defaultNumPartitions = sc.defaultParallelism

  def read(id: LayerId, rasterQuery: RDDQuery[SpaceTimeKey, M], numPartitions: Int) = {
    if (!attributeStore.layerExists(id)) throw new LayerNotFoundError(id)

    val header = attributeStore.read[AccumuloLayerHeader](id, "header")
    val metaData = attributeStore.read[M](id, "metadata")
    val keyBounds = attributeStore.read[KeyBounds[SpaceTimeKey]](id, "keyBounds")
    val schema = attributeStore.read[Schema](id, "schema")

    val queryKeyBounds = rasterQuery(metaData, keyBounds)

    val decompose = (bounds: KeyBounds[SpaceTimeKey]) =>
      CustomKeyIndex.indexRanges(bounds).map { case (min, max) =>
        new AccumuloRange(min, max)
      }

    val rdd = readRdd[V](header.tileTable, columnFamily(id), queryKeyBounds, schema)
    new ContextRDD(rdd, metaData)
  }

  private def readRdd[V: AvroRecordCodec: ClassTag](
    table: String,
    columnFamily: String,
    queryKeyBounds: Seq[KeyBounds[SpaceTimeKey]],
    writerSchema: Schema
  ): RDD[(SpaceTimeKey, V)] = {
    val codec = KryoWrapper(KeyValueRecordCodec[SpaceTimeKey, V])
    val boundable = implicitly[Boundable[SpatialKey]]
    val kwWriterSchema = KryoWrapper(writerSchema)

    val job = Job.getInstance(sc.hadoopConfiguration)
    instance.setAccumuloConfig(job)
    InputFormatBase.setInputTableName(job, table)

    val includeKey = (key: SpaceTimeKey) => KeyBounds.includeKey(queryKeyBounds, key)(SpaceTimeKey.Boundable)
    val timeText = (key: SpaceTimeKey) => new Text(key.time.withZone(DateTimeZone.UTC).toString)

    val queryBoundRDDs =
      queryKeyBounds
        .map { bounds =>
          val ranges =
            CustomKeyIndex.indexRanges(bounds).map { case (min, max) =>
              new AccumuloRange(min, max)
            }

          InputFormatBase.setRanges(job, ranges)
          InputFormatBase.fetchColumns(job, List(new AccumuloPair(new Text(columnFamily), null: Text)))
          InputFormatBase.addIterator(job,
            new IteratorSetting(2,
              "TimeColumnFilter",
              "org.apache.accumulo.core.iterators.user.ColumnSliceFilter",
              Map("startBound" -> bounds.minKey.time.toString,
                "endBound" -> bounds.maxKey.time.toString,
                "startInclusive" -> "true",
                "endInclusive" -> "true")))

          sc.newAPIHadoopRDD(
            job.getConfiguration,
            classOf[BatchAccumuloInputFormat],
            classOf[Key],
            classOf[Value])
        }
        .map { rdd =>
          rdd
            .flatMap { case (_, value) =>
              val pairs = AvroEncoder.fromBinary(kwWriterSchema.value, value.get)(codec.value)
              pairs.filter{ pair => includeKey(pair._1) }
            }
        }
    sc.union(queryBoundRDDs)
  }
}
