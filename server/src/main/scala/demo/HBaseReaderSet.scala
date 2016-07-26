package demo

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.hbase._
import geotrellis.spark.io.json._
import geotrellis.spark.io.index._
import geotrellis.spark.io.avro._
import geotrellis.spark.io.avro.codecs._

import org.apache.avro.Schema
import org.apache.spark._

import spray.json._
import spray.json.DefaultJsonProtocol._

class HBaseReaderSet(instance: HBaseInstance)(implicit sc: SparkContext) extends ReaderSet {
  val attributeStore = HBaseAttributeStore(instance)
  val metadataReader = new MetadataReader(attributeStore)
  val layerReader = HBaseLayerReader(instance)
  val singleBandTileReader = new TileReader[SpaceTimeKey, Tile](HBaseValueReader(instance))
  val multiBandTileReader = new TileReader[SpaceTimeKey, MultibandTile](HBaseValueReader(instance))
}
