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
    new MetadataReader[SpaceTimeKey] {
      def initialRead(layer: LayerId) = {
        val rmd = attributeStore.readLayerAttributes[S3LayerHeader, TileLayerMetadata[SpaceTimeKey], SpaceTimeKey](layer).metadata
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

  val singleBandLayerReader = S3LayerReader(attributeStore)
  val singleBandTileReader = new TileReader(S3TileReader[SpaceTimeKey, Tile](bucket, prefix))

  val multiBandLayerReader = S3LayerReader(attributeStore)
  val multiBandTileReader = new TileReader(S3TileReader[SpaceTimeKey, MultibandTile](bucket, prefix))
}
