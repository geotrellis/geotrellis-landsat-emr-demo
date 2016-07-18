package demo

import demo.avro._

import com.azavea.landsatutil.{MTL, MtlArray}
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.file._
import geotrellis.spark.io.json._
import geotrellis.spark.io.index._
import geotrellis.spark.io.avro._
import geotrellis.spark.io.avro.codecs._
import org.apache.avro.Schema
import org.apache.spark._
import spray.json._
import spray.json.DefaultJsonProtocol._

class FileReaderSet(path: String)(implicit sc: SparkContext) extends ReaderSet {
  val attributeStore = FileAttributeStore(path)
  val metadataReader = new MetadataReader(attributeStore)
  val layerReader = FileLayerReader(attributeStore)
  val singleBandTileReader = new TileReader[SpaceTimeKey, TileFeature[Tile, Array[MTL]]](FileValueReader(path))
  val multiBandTileReader = new TileReader[SpaceTimeKey, TileFeature[MultibandTile, Array[MTL]]](FileValueReader(path))
}
