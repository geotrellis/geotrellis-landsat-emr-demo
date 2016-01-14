package demo

import geotrellis.raster._

object NDWI extends (MultiBandTile => Tile) {
  def apply(tile: MultiBandTile): Tile =
    tile.convert(TypeDouble).combineDouble(1, 3) { (g, nir) =>
      if(100 < g && 100 < nir) {
        (g - nir) / (g + nir)
      } else {
        Double.NaN
      }
    }
}
