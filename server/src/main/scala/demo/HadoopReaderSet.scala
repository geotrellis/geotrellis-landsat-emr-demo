package demo

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.hadoop._

import org.apache.hadoop.fs.Path
import org.apache.spark.SparkContext

class HadoopReaderSet(path: Path)(implicit sc: SparkContext) extends ReaderSet {
  val attributeStore = HadoopAttributeStore(path)
  val metadataReader = new MetadataReader(attributeStore)
  val layerReader = HadoopLayerReader(attributeStore)
  val layerCReader = HadoopLayerCollectionReader(attributeStore)
  val singleBandTileReader = new TileReader[SpaceTimeKey, Tile](HadoopValueReader(path))
  val multiBandTileReader = new TileReader[SpaceTimeKey, MultibandTile](HadoopValueReader(path))
}
