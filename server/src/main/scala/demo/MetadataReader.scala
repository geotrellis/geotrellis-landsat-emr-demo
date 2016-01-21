package demo


import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.tiling._

import spray.json._

import java.util.concurrent.ConcurrentHashMap

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

  lazy val layerNamesToMaxZooms: Map[String, Int] =
    layerNamesToZooms.mapValues(_.max)

  def readLayerAttribute[T: JsonFormat](layerName: String, attributeName: String): T
}
