package demo

import com.azavea.landsatutil.MTL
import geotrellis.raster._
import geotrellis.util.MethodExtensions

package object prototype extends Serializable {
  implicit class withSinglebandTileFeaturePrototypeMethods(val self: TileFeature[Tile, MTL])
    extends MethodExtensions[TileFeature[Tile, MTL]] with SinglebandTileFeaturePrototypeMethods

  implicit class withMultibandTileFeaturePrototypeMethods(val self: TileFeature[MultibandTile, MTL])
    extends MethodExtensions[TileFeature[MultibandTile, MTL]] with MultibandTileFeaturePrototypeMethods
}
