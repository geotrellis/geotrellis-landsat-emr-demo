package demo

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.json._
import geotrellis.spark.io.index._
import geotrellis.spark.io.avro._
import geotrellis.spark.io.avro.codecs._

import org.apache.avro.Schema
import org.apache.spark._

import spray.json._
import spray.json.DefaultJsonProtocol._

class S3ReaderSet(bucket: String, prefix: String)(implicit sc: SparkContext) extends ReaderSet {
  val attributeStore = S3AttributeStore(bucket, prefix)

  val metadataReader =
    new MetadataReader {
      def initialRead(layer: LayerId) = {
        val rmd = attributeStore.readLayerAttributes[S3LayerHeader, RasterMetaData, KeyBounds[SpaceTimeKey], KeyIndex[SpaceTimeKey], Schema](layer)._2
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

  val singleBandLayerReader = S3LayerReader[SpaceTimeKey, Tile, RasterMetaData](attributeStore)
  val singleBandTileReader = new CachingTileReader(S3TileReader[SpaceTimeKey, Tile](bucket, prefix))

  val multiBandLayerReader = S3LayerReader[SpaceTimeKey, MultiBandTile, RasterMetaData](attributeStore)
  val multiBandTileReader = new CachingTileReader(S3TileReader[SpaceTimeKey, MultiBandTile](bucket, prefix))
}
