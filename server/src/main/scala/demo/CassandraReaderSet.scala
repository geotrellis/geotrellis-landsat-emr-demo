package demo

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.cassandra._
import geotrellis.spark.io.json._
import geotrellis.spark.io.index._
import geotrellis.spark.io.avro._
import geotrellis.spark.io.avro.codecs._

import org.apache.avro.Schema
import org.apache.spark._

import spray.json._
import spray.json.DefaultJsonProtocol._

class CassandraReaderSet(instance: CassandraInstance)(implicit sc: SparkContext) extends ReaderSet {
  val attributeStore = CassandraAttributeStore(instance)
  val metadataReader = new MetadataReader(attributeStore)
  val layerReader = CassandraLayerReader(instance)
  val singleBandTileReader = new TileReader[SpaceTimeKey, Tile](CassandraValueReader(instance))
  val multiBandTileReader = new TileReader[SpaceTimeKey, MultibandTile](CassandraValueReader(instance))
}
