// package demo

// import geotrellis.spark._
// import geotrellis.spark.io._
// import geotrellis.spark.io.accumulo._
// import geotrellis.spark.io.avro._
// import geotrellis.spark.io.avro.codecs._
// import geotrellis.spark.io.index._
// import geotrellis.spark.io.index.zcurve._
// import geotrellis.spark.io.json._

// import org.apache.accumulo.core.data.{Key, Value}
// import org.apache.hadoop.io.Text
// import org.apache.spark.SparkContext
// import org.apache.spark.rdd.RDD
// import org.joda.time.DateTimeZone
// import spray.json._

// import java.nio.charset.StandardCharsets
// import scala.collection.mutable
// import scala.collection.JavaConversions._
// import scala.reflect._

// object CustomKeyIndex {
//   val spatialKeyIndex = new ZSpatialKeyIndex()

//   def toIndex(key: SpaceTimeKey): Text = {
//     val spatialIndex = spatialKeyIndex.toIndex(key.spatialKey)
//     val timeSuffix = s"_${key.time.getYear.toString}"
//     new Text(long2Bytes(spatialIndex) ++ timeSuffix.getBytes(StandardCharsets.UTF_8))
//   }

//   def indexRanges(keyRange: (SpaceTimeKey, SpaceTimeKey)): Seq[(Text, Text)] = {
//     val spatialRanges = spatialKeyIndex.indexRanges((keyRange._1.spatialKey, keyRange._2.spatialKey))
//     val startYearSuffix = s"_${keyRange._1.time.getYear.toString}"
//     val endYearSuffix = s"_${keyRange._2.time.getYear.toString}"
//     spatialRanges.map { case (i1, i2) =>
//       val start = new Text(long2Bytes(i1) ++ startYearSuffix.getBytes(StandardCharsets.UTF_8))
//       val end = new Text(long2Bytes(i2) ++ endYearSuffix.getBytes(StandardCharsets.UTF_8))
//       (start, end)
//     }
//   }
// }

// class CustomAccumuloLayerWriter[V: AvroRecordCodec: ClassTag, M: JsonFormat](
//   instance: AccumuloInstance,
//   table: String
// ) extends Writer[LayerId, RDD[(SpaceTimeKey, V)] with Metadata[M]] {

//   def ensureTableExists(tableName: String): Unit = {
//     val ops = instance.connector.tableOperations()
//     if (! ops.exists(tableName))
//       ops.create(tableName)
//   }

//   def makeLocalityGroup(tableName: String, columnFamily: String): Unit = {
//     val ops = instance.connector.tableOperations()
//     val groups = ops.getLocalityGroups(tableName)
//     val newGroup: java.util.Set[Text] = Set(new Text(columnFamily))
//     ops.setLocalityGroups(tableName, groups.updated(tableName, newGroup))
//   }

//   val strategy = HdfsWriteStrategy("/geotrellis-ingest")
//   val attributeStore = AccumuloAttributeStore(instance.connector)

//   def write(id: LayerId, rdd: RDD[(SpaceTimeKey, V)] with Metadata[M]): Unit = {
//     val codec  = KeyValueRecordCodec[SpaceTimeKey, V]
//     val schema = codec.schema

//     val header =
//       AccumuloLayerHeader(
//         keyClass = classTag[SpaceTimeKey].toString(),
//         valueClass = classTag[V].toString(),
//         tileTable = table
//       )
//     val metaData = rdd.metadata
//     val keyBounds = implicitly[Boundable[SpaceTimeKey]].getKeyBounds(rdd)

//     try {
//       attributeStore.write(id, "header", header)
//       attributeStore.write(id, "metadata", metaData)
//       attributeStore.write(id, "keyBounds", keyBounds)
//       attributeStore.write(id, "schema", schema)

//       implicit val sc = rdd.sparkContext

//       ensureTableExists(table)
//       makeLocalityGroup(table, columnFamily(id))

//       val cf = columnFamily(id)

//       val _codec = codec
//       val kvPairs =
//         rdd
//           .map { case tuple @ (key, _) =>
//             val value: Value = new Value(AvroEncoder.toBinary(Vector(tuple))(_codec))
//             val rowKey = new Key(CustomKeyIndex.toIndex(key), cf, new Text(key.time.withZone(DateTimeZone.UTC).toString))
//             (rowKey, value)
//           }

//       strategy.write(kvPairs, instance, table)
//     } catch {
//       case e: Exception => throw new LayerWriteError(id).initCause(e)
//     }
//   }
// }
