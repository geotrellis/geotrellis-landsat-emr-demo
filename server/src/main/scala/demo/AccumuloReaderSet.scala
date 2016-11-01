package demo

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.accumulo._

import org.apache.spark.SparkContext

class AccumuloReaderSet(instance: AccumuloInstance)(implicit sc: SparkContext) extends ReaderSet {
  val attributeStore = AccumuloAttributeStore(instance.connector)
  val metadataReader = new MetadataReader(attributeStore)
  val layerReader = AccumuloLayerReader(instance)
  val layerCReader = AccumuloCollectionLayerReader(instance)
  val singleBandTileReader = new TileReader[SpaceTimeKey, Tile](AccumuloValueReader(instance))
  val multiBandTileReader = new TileReader[SpaceTimeKey, MultibandTile](AccumuloValueReader(instance))
}
