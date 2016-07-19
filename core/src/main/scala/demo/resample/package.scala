package demo

import geotrellis.raster.{MultibandTile, Tile, TileFeature}
import geotrellis.util.MethodExtensions

package object resample {
  implicit class withSinglebandTileFeatureResampleMethods[D](val self: TileFeature[Tile, D])
    extends MethodExtensions[TileFeature[Tile, D]] with SinglebandTileFeatureResampleMethods[D]

  implicit class withMultibandTileFeatureResampleMethods[D](val self: TileFeature[MultibandTile, D])
    extends MethodExtensions[TileFeature[MultibandTile, D]] with MultibandTileFeatureResampleMethods[D]
}
