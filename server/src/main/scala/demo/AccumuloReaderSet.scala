package demo

import demo.avro._

import com.azavea.landsatutil.MTL
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
  val layerReader = AccumuloLayerReader(instance)
  val singleBandTileReader = new TileReader[SpaceTimeKey, TileFeature[Tile, MTL]](AccumuloValueReader(instance))
  val multiBandTileReader = new TileReader[SpaceTimeKey, TileFeature[MultibandTile, MTL]](AccumuloValueReader(instance))
}
