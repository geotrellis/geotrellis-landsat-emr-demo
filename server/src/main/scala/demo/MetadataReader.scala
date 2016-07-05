package demo


import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.json._
import geotrellis.spark.tiling._

import spray.json._
import spray.json.DefaultJsonProtocol._
import java.util.concurrent.ConcurrentHashMap

/** Aside from reading our metadata we also do some processing to figure out how many time stamps we have */
class MetadataReader(attributeStore: AttributeStore) {
  def read[K: SpatialComponent: JsonFormat](layer: LayerId) = {
    val md = attributeStore.readMetadata[TileLayerMetadata[K]](layer)
    val times = attributeStore.read[Array[Long]](LayerId(layer.name, 0), "times")
    LayerMetadata(md, times)
  }

  lazy val layerNamesToZooms =
    attributeStore.layerIds
      .groupBy(_.name)
      .map { case (name, layerIds) => (name, layerIds.map(_.zoom).sorted.toArray) }
      .toMap

  lazy val layerNamesToMaxZooms: Map[String, Int] =
    layerNamesToZooms.mapValues(_.max)

  /** Read an attribute that pertains to all the zooms of the layer
    * by convention this is stored for zoom level 0 */
  def readLayerAttribute[T: JsonFormat](layerName: String, attributeName: String): T =
    attributeStore.read[T](LayerId(layerName, 0), attributeName)
}
