package demo

import geotrellis.spark.TileLayerMetadata

case class LayerMetadata[K](tileLayerMetadata: TileLayerMetadata[K], times: Array[Long])
