package demo

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.cassandra._

import org.apache.spark.SparkContext

class CassandraReaderSet(instance: CassandraInstance)(implicit sc: SparkContext) extends ReaderSet {
  val attributeStore = CassandraAttributeStore(instance)
  val metadataReader = new MetadataReader(attributeStore)
  val layerReader = CassandraLayerReader(instance)
  val layerCReader = CassandraCollectionLayerReader(instance)
  val singleBandTileReader = new TileReader[SpaceTimeKey, Tile](CassandraValueReader(instance))
  val multiBandTileReader = new TileReader[SpaceTimeKey, MultibandTile](CassandraValueReader(instance))
}
