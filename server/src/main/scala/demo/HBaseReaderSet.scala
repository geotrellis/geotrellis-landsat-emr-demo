package demo

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.hbase._

import org.apache.spark.SparkContext

class HBaseReaderSet(instance: HBaseInstance)(implicit sc: SparkContext) extends ReaderSet {
  val attributeStore = HBaseAttributeStore(instance)
  val metadataReader = new MetadataReader(attributeStore)
  val layerReader = HBaseLayerReader(instance)
  val layerCReader = HBaseLayerCollectionReader(instance)
  val singleBandTileReader = new TileReader[SpaceTimeKey, Tile](HBaseValueReader(instance))
  val multiBandTileReader = new TileReader[SpaceTimeKey, MultibandTile](HBaseValueReader(instance))
}
