package demo.resample

import geotrellis.raster.{MultibandTile, Raster, RasterExtent, TileFeature}
import geotrellis.raster.resample.{ResampleMethod, TileResampleMethods}
import geotrellis.vector.Extent

trait MultibandTileFeatureResampleMethods[D] extends TileResampleMethods[TileFeature[MultibandTile, D]] {
  def resample(extent: Extent, target: RasterExtent, method: ResampleMethod): TileFeature[MultibandTile, D] =
    TileFeature(Raster(self.tile, extent).resample(target, method).tile, self.data)

  def resample(extent: Extent, targetCols: Int, targetRows: Int, method: ResampleMethod): TileFeature[MultibandTile, D] =
    TileFeature(Raster(self.tile, extent).resample(targetCols, targetRows, method).tile, self.data)
}
