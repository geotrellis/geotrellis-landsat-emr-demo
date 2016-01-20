package demo

import geotrellis.spark._
import geotrellis.spark.io.accumulo._
import geotrellis.spark.io.index.KeyIndex
import geotrellis.spark.{KeyBounds, LayerId}
import geotrellis.spark.io.AttributeStore.Fields
import geotrellis.spark.io.s3.S3LayerHeader
import geotrellis.spark.io.{CatalogError, TileNotFoundError, Reader}
import geotrellis.spark.io.avro._
import geotrellis.spark.io.avro.codecs._
import geotrellis.spark.io.json._
import org.apache.accumulo.core.data.{Value, Range => ARange}
import org.apache.accumulo.core.security.Authorizations
import org.apache.avro.Schema
import org.apache.hadoop.io.Text
import org.joda.time.DateTimeZone
import spray.json._
import spray.json.DefaultJsonProtocol._
import scala.collection.JavaConversions._

import scala.reflect.ClassTag

class CustomAccumuloTileReader[V: AvroRecordCodec](
    instance: AccumuloInstance)
  extends Reader[LayerId, Reader[SpaceTimeKey, V]] {
  val attributeStore = AccumuloAttributeStore(instance.connector)

  def read(layerId: LayerId): Reader[SpaceTimeKey, V] = new Reader[SpaceTimeKey, V] {
    val codec = KeyValueRecordCodec[SpaceTimeKey, V]
    val layerMetaData = attributeStore.read[AccumuloLayerHeader](layerId, "header")
    val schema = attributeStore.read[Schema](layerId, "schema")

    def read(key: SpaceTimeKey): V = {
      val scanner = instance.connector.createScanner(layerMetaData.tileTable, new Authorizations())
      scanner.setRange(new ARange(CustomKeyIndex.toIndex(key)))
      scanner.fetchColumn(new Text(columnFamily(layerId)), new Text(key.time.withZone(DateTimeZone.UTC).toString))

      val tiles = scanner.iterator
        .map { entry =>
          AvroEncoder.fromBinary(schema, entry.getValue.get)(codec)
        }
        .flatMap { pairs: Vector[(SpaceTimeKey, V)] =>
          pairs.filter(pair => pair._1 == key)
        }
        .toVector

      if (tiles.isEmpty) {
        throw new TileNotFoundError(key, layerId)
      } else if (tiles.size > 1) {
        throw new CatalogError(s"Multiple tiles found for $key for layer $layerId")
      } else {
        tiles.head._2
      }
    }
  }
}
