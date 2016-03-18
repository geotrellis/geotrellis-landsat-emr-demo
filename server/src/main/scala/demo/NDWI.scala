package demo

import geotrellis.raster._

object NDWI extends (MultibandTile => Tile) {
  def apply(tile: MultibandTile): Tile =
    tile.convert(DoubleCellType).combineDouble(1, 3) { (g, nir) =>
      (g - nir) / (g + nir)
    }
}
