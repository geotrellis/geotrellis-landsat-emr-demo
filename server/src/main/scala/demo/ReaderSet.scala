package demo

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.avro._
import geotrellis.spark.tiling._

import com.github.nscala_time.time.Imports._
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

  lazy val layerNamesToMaxZooms: Map[String, Int] =
    layerNamesToZooms.mapValues(_.max)

  def readLayerAttribute[T: JsonFormat](layerName: String, attributeName: String): T
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
  val layoutScheme = ZoomedLayoutScheme(WebMercator, 256)

  def metadataReader: MetadataReader
  def singleBandLayerReader: FilteringLayerReader[LayerId, SpaceTimeKey, RasterMetaData, RasterRDD[SpaceTimeKey]]
  def singleBandTileReader: CachingTileReader[SpaceTimeKey, Tile]
  def multiBandLayerReader: FilteringLayerReader[LayerId, SpaceTimeKey, RasterMetaData, MultiBandRasterRDD[SpaceTimeKey]]
  def multiBandTileReader: CachingTileReader[SpaceTimeKey, MultiBandTile]

  def readSingleBandTile(layer: String, zoom: Int, x: Int, y: Int, time: DateTime): Option[Tile] =
    try {
      val z = metadataReader.layerNamesToMaxZooms(layer)

      if(zoom > z) {
        val layerId = LayerId(layer, z)

        val meta = metadataReader.read(layerId)
        val rmd = meta.rasterMetaData

        val requestZoomMapTransform = layoutScheme.levelForZoom(zoom).layout.mapTransform
        val requestExtent = requestZoomMapTransform(x, y)
        val centerPoint = requestZoomMapTransform(x, y).center
        val SpatialKey(nx, ny) = rmd.mapTransform(centerPoint)
        val sourceExtent = rmd.mapTransform(nx, ny)


        val largerTile =
          singleBandTileReader.read(layerId, SpaceTimeKey(nx, ny, time))

        Some(largerTile.resample(sourceExtent, RasterExtent(requestExtent, 256, 256), Bilinear))
      } else {
        Some(singleBandTileReader.read(LayerId(layer, zoom), SpaceTimeKey(x, y, time)))
      }
    } catch {
      case e: TileNotFoundError =>
        None
    }

  def readMultiBandTile(layer: String, zoom: Int, x: Int, y: Int, time: DateTime): Option[MultiBandTile] =
    try {
      val z = metadataReader.layerNamesToMaxZooms(layer)

      if(zoom > z) {
        val layerId = LayerId(layer, z)

        val meta = metadataReader.read(layerId)
        val rmd = meta.rasterMetaData

        val requestZoomMapTransform = layoutScheme.levelForZoom(zoom).layout.mapTransform
        val requestExtent = requestZoomMapTransform(x, y)
        val centerPoint = requestZoomMapTransform(x, y).center
        val SpatialKey(nx, ny) = rmd.mapTransform(centerPoint)
        val sourceExtent = rmd.mapTransform(nx, ny)


        val largerTile =
          multiBandTileReader.read(layerId, SpaceTimeKey(nx, ny, time))

        Some(largerTile.resample(sourceExtent, RasterExtent(requestExtent, 256, 256), Bilinear))
      } else {
        Some(multiBandTileReader.read(LayerId(layer, zoom), SpaceTimeKey(x, y, time)))
      }
    } catch {
      case e: TileNotFoundError =>
        None
    }
}
