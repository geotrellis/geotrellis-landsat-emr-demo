package demo


import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.tiling._

import spray.json._

import java.util.concurrent.ConcurrentHashMap

trait MetadataReader[K] extends Reader[LayerId, LayerMetadata[K]] {
  private val cache = new ConcurrentHashMap[LayerId, LayerMetadata[K]]

  def read(layerId: LayerId): LayerMetadata[K] =
    if(cache.containsKey(layerId))
      cache.get(layerId)
    else {
      val value = initialRead(layerId)
      cache.put(layerId, value)
      value
    }

  def initialRead(layerId: LayerId): LayerMetadata[K]

  def layerNamesToZooms: Map[String, Array[Int]]

  lazy val layerNamesToMaxZooms: Map[String, Int] =
    layerNamesToZooms.mapValues(_.max)

  def readLayerAttribute[T: JsonFormat](layerName: String, attributeName: String): T
}
