package demo

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.json._
import geotrellis.spark.io.index._
import geotrellis.spark.io.avro._
import geotrellis.spark.io.avro.codecs._

import org.apache.hadoop.fs._
import org.apache.avro.Schema
import org.apache.spark._

import spray.json._
import spray.json.DefaultJsonProtocol._

class HadoopReaderSet(path: Path)(implicit sc: SparkContext) extends ReaderSet {
  val attributeStore = HadoopAttributeStore(path)
  val metadataReader = new MetadataReader(attributeStore)
  val layerReader = HadoopLayerReader(attributeStore)
  val singleBandTileReader = new TileReader[SpaceTimeKey, Tile](HadoopValueReader(path))
  val multiBandTileReader = new TileReader[SpaceTimeKey, MultibandTile](HadoopValueReader(path))
}
