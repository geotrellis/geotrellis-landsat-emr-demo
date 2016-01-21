package demo

import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.avro._

import spray.json._

import java.util.concurrent.ConcurrentHashMap
import scala.reflect._

class TileReader[K: AvroRecordCodec: JsonFormat: ClassTag, V: AvroRecordCodec](tileReader: Reader[LayerId, Reader[K, V]]) {
  private val cache = new ConcurrentHashMap[LayerId, Reader[K, V]]

  def read(layerId: LayerId, key: K): V = {
    val reader =
      if(cache.containsKey(layerId))
        cache.get(layerId)
      else {
        val value = tileReader(layerId)
        cache.put(layerId, value)
        value
      }
    reader.read(key)
  }
}
