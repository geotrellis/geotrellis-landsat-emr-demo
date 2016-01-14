package demo

import geotrellis.raster._

object NDVI {
  def apply(tile: MultiBandTile): Tile =
    tile.convert(TypeDouble).combineDouble(0, 3) { (r, nir) =>
      if(100 < r && 100 < nir) {
        (nir - r) / (nir + r)
      } else {
        Double.NaN
      }
    }
}
