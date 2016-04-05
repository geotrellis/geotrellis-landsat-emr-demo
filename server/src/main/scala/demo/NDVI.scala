package demo

import geotrellis.raster._

object NDVI {
  def apply(tile: MultibandTile): Tile =
    tile.convert(DoubleCellType).combineDouble(0, 3) { (r, nir) =>
      (nir - r) / (nir + r)
    }
}
