package demo

import geotrellis.raster._

object NDVI {
  def apply(tile: MultiBandTile): Tile =
    tile.convert(TypeDouble).combineDouble(0, 3) { (r, nir) =>
      (nir - r) / (nir + r)
    }
}
