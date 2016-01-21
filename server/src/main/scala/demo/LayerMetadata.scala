package demo

import geotrellis.spark.RasterMetaData

case class LayerMetadata(rasterMetaData: RasterMetaData, times: Array[Long])
