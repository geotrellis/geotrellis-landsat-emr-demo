package demo

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._

import org.apache.spark.SparkContext

class FileReaderSet(path: String)(implicit sc: SparkContext) extends ReaderSet {
  val attributeStore = FileAttributeStore(path)
  val metadataReader = new MetadataReader(attributeStore)
  val layerReader = FileLayerReader(attributeStore)
  val singleBandTileReader = new TileReader[SpaceTimeKey, Tile](FileValueReader(path))
  val multiBandTileReader = new TileReader[SpaceTimeKey, MultibandTile](FileValueReader(path))
}
