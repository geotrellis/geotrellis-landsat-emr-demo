package demo

import com.azavea.landsatutil.MTL
import geotrellis.raster.{MultibandTile, TileFeature}
import geotrellis.util.MethodExtensions

package object prototype extends Serializable {
  implicit class withMultibandTileFeaturePrototypeMethods(val self: TileFeature[MultibandTile, MTL])
    extends MethodExtensions[TileFeature[MultibandTile, MTL]] with MultibandTileFeaturePrototypeMethods
}
