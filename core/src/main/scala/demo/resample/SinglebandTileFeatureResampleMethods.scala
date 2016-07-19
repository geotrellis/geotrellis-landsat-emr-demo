package demo.resample

import geotrellis.raster.{Raster, RasterExtent, Tile, TileFeature}
import geotrellis.raster.resample.{ResampleMethod, TileResampleMethods}
import geotrellis.vector.Extent

trait SinglebandTileFeatureResampleMethods[D] extends TileResampleMethods[TileFeature[Tile, D]] {
  def resample(extent: Extent, target: RasterExtent, method: ResampleMethod): TileFeature[Tile, D] =
    TileFeature(Raster(self.tile, extent).resample(target, method).tile, self.data)

  def resample(extent: Extent, targetCols: Int, targetRows: Int, method: ResampleMethod): TileFeature[Tile, D] =
    TileFeature(Raster(self.tile, extent).resample(targetCols, targetRows, method).tile, self.data)
}

