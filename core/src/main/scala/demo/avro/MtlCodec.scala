package demo.avro

import com.azavea.landsatutil.{MTL, MtlGroup}
import geotrellis.spark.io.avro.AvroRecordCodec
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericRecord

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

trait MtlCodec {
  implicit def mtlGroupCodec: AvroRecordCodec[MtlGroup] = new AvroRecordCodec[MtlGroup] {
    def schema = SchemaBuilder
      .record("MtlGroup").namespace("com.azavea.landsatutil")
      .fields()
      .name("name").`type`().stringType().noDefault()
      .name("fields").`type`().map().values().stringType().noDefault()
      .endRecord()

    def encode(mgroup: MtlGroup, rec: GenericRecord) = {
      rec.put("name", mgroup.name)
      rec.put("fields", mapAsJavaMap(mgroup.fields.map { case (k, v) =>
        k -> (Try { v.toString } match {
          case Success(s) => s
          case Failure(e) => ""
        })
      }))
    }

    def decode(rec: GenericRecord) =
      new MtlGroup(rec.get("name").asInstanceOf[String], rec.get("fields").asInstanceOf[java.util.Map[java.lang.String, java.lang.String]].toMap)
  }

  implicit def mtlCodec: AvroRecordCodec[MTL] = new AvroRecordCodec[MTL] {
    def schema = SchemaBuilder
      .record("MTL").namespace("com.azavea.landsatutil")
      .fields()
      .name("group").`type`().map().values().`type`(mtlGroupCodec.schema).noDefault()
      .endRecord()

    def encode(mtl: MTL, rec: GenericRecord) =
      rec.put("group", mapAsJavaMap(mtl.group.map { case (k, v) => k -> mtlGroupCodec.encode(v) }))

    def decode(rec: GenericRecord) =
      new MTL(rec.get("group").asInstanceOf[java.util.Map[java.lang.String, MtlGroup]].toMap)
  }

  implicit def mtlArrayCodec: AvroRecordCodec[Array[MTL]] = new AvroRecordCodec[Array[MTL]] {
    def schema = SchemaBuilder
      .record("MtlArray").namespace("com.azavea.landsatutil")
      .fields()
      .name("array").`type`().array().items().`type`(mtlCodec.schema).noDefault()
      .endRecord()

    def encode(mtl: Array[MTL], rec: GenericRecord) =
      rec.put("array", java.util.Arrays.asList(mtl.array.map(mtlCodec.encode(_)): _*))

    def decode(rec: GenericRecord) =
      rec.get("array")
        .asInstanceOf[java.util.Collection[MTL]]
        .asScala
        .toArray[MTL]
  }
}
