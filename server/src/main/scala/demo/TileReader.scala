package demo

import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.avro._

import spray.json._

import scala.collection.concurrent.TrieMap
import scala.reflect._

class TileReader[K: AvroRecordCodec: JsonFormat: ClassTag, V: AvroRecordCodec](
  valueReader: ValueReader[LayerId]
) {
  private val cache = new TrieMap[LayerId, Reader[K, V]]

  def read(layerId: LayerId, key: K): V = {
    val reader = cache.getOrElseUpdate(layerId, valueReader.reader[K,V](layerId))
    reader.read(key)
  }
}
