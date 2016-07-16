package demo

import demo.avro._
import demo.merge._
import demo.prototype._
import demo.resample._
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

import com.azavea.landsatutil.MTL

import scala.reflect._

trait ReaderSet {
  val layoutScheme = ZoomedLayoutScheme(WebMercator, 256)
  def attributeStore: AttributeStore
  def metadataReader: MetadataReader
  def layerReader: FilteringLayerReader[LayerId]
  def singleBandTileReader: TileReader[SpaceTimeKey, TileFeature[Tile, MTL]]
  def multiBandTileReader: TileReader[SpaceTimeKey, TileFeature[MultibandTile, MTL]]

  /** Do "overzooming", where we resample lower zoom level tiles to serve out higher zoom level tiles. */
  def readSinglebandTile(layer: String, zoom: Int, x: Int, y: Int, time: DateTime): Option[TileFeature[Tile, MTL]] =
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

  /** Do "overzooming", where we resample lower zoom level tiles to serve out higher zoom level tiles. */
  def readMultibandTile(layer: String, zoom: Int, x: Int, y: Int, time: DateTime): Option[TileFeature[MultibandTile, MTL]] =
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
