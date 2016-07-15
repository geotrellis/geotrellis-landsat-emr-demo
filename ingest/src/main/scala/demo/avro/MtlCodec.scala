package demo.avro

import com.azavea.landsatutil.{MTL, MtlGroup}
import geotrellis.spark.io.avro.AvroRecordCodec
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericRecord

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
      rec.put("fields", mgroup.fields.map { case (k, v) => k -> v.toString })
    }

    def decode(rec: GenericRecord) =
      new MtlGroup(rec.get("name").asInstanceOf[String], rec.get("fields").asInstanceOf[Map[String, String]])
  }

  implicit def mtlCodec: AvroRecordCodec[MTL] = new AvroRecordCodec[MTL] {
    def schema = SchemaBuilder
      .record("MTL").namespace("com.azavea.landsatutil")
      .fields()
      .name("group").`type`().map().values().`type`(mtlGroupCodec.schema).noDefault()
      .endRecord()

    def encode(mtl: MTL, rec: GenericRecord) =
      rec.put("group", mtl.group)

    def decode(rec: GenericRecord) =
      new MTL(rec.get("group").asInstanceOf[Map[String, MtlGroup]])
  }
}
