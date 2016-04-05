package demo

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.accumulo._
import geotrellis.spark.io.json._
import geotrellis.spark.io.index._
import geotrellis.spark.io.avro._
import geotrellis.spark.io.avro.codecs._

import org.apache.avro.Schema
import org.apache.spark._

import spray.json._
import spray.json.DefaultJsonProtocol._

class AccumuloReaderSet(instance: AccumuloInstance)(implicit sc: SparkContext) extends ReaderSet {
  val attributeStore = AccumuloAttributeStore(instance.connector)

  val metadataReader = new MetadataReader(attributeStore)

  val singleBandLayerReader = AccumuloLayerReader(instance)
  val singleBandTileReader = new TileReader(AccumuloTileReader[SpaceTimeKey, Tile](instance))

  val multiBandLayerReader = AccumuloLayerReader(instance)
  val multiBandTileReader = new TileReader(AccumuloTileReader[SpaceTimeKey, MultibandTile](instance))
}
