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

class CustomAccumuloReaderSet(instance: AccumuloInstance)(implicit sc: SparkContext) extends ReaderSet {
  val attributeStore = AccumuloAttributeStore(instance.connector)

  val metadataReader =
    new MetadataReader {
      def initialRead(layer: LayerId) = {
        val rmd = attributeStore.read[RasterMetaData](layer, "metadata")
        val times = attributeStore.read[Array[Long]](LayerId(layer.name, 0), "times")
        LayerMetadata(rmd, times)
      }

      def layerNamesToZooms =
        attributeStore.layerIds
          .groupBy(_.name)
          .map { case (name, layerIds) => (name, layerIds.map(_.zoom).sorted.toArray) }
          .toMap

      def readLayerAttribute[T: JsonFormat](layerName: String, attributeName: String): T =
        attributeStore.read[T](LayerId(layerName, 0), attributeName)
    }

  val singleBandLayerReader = new CustomAccumuloLayerReader[Tile, RasterMetaData](instance)
  val singleBandTileReader = new CachingTileReader(new CustomAccumuloTileReader[Tile](instance))

  val multiBandLayerReader = new CustomAccumuloLayerReader[MultiBandTile, RasterMetaData](instance)
  val multiBandTileReader = new CachingTileReader(new CustomAccumuloTileReader[MultiBandTile](instance))
}
