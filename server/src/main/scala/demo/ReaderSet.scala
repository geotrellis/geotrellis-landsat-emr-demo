package demo

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.avro._

import spray.json._

import java.util.concurrent.ConcurrentHashMap
import scala.reflect._

case class LayerMetadata(rasterMetaData: RasterMetaData, times: Array[Long])

trait MetadataReader extends Reader[LayerId, LayerMetadata] {
  private val cache = new ConcurrentHashMap[LayerId, LayerMetadata]

  def read(layerId: LayerId): LayerMetadata =
    if(cache.containsKey(layerId))
      cache.get(layerId)
    else {
      val value = initialRead(layerId)
      cache.put(layerId, value)
      value
    }

  def initialRead(layerId: LayerId): LayerMetadata

  def layerNamesToZooms: Map[String, Array[Int]]
}

class CachingTileReader[K: AvroRecordCodec: JsonFormat: ClassTag, V: AvroRecordCodec](tileReader: Reader[LayerId, Reader[K, V]]) {
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

trait ReaderSet {
  def metadataReader: MetadataReader
  def singleBandLayerReader: FilteringLayerReader[LayerId, SpaceTimeKey, RasterMetaData, RasterRDD[SpaceTimeKey]]
  def singleBandTileReader: CachingTileReader[SpaceTimeKey, Tile]
  def multiBandLayerReader: FilteringLayerReader[LayerId, SpaceTimeKey, RasterMetaData, MultiBandRasterRDD[SpaceTimeKey]]
  def multiBandTileReader: CachingTileReader[SpaceTimeKey, MultiBandTile]
}
