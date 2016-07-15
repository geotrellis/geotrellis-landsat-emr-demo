package demo

import com.azavea.landsatutil.MTL
import geotrellis.raster._
import geotrellis.util._

package object merge extends Serializable {
  implicit class withMultibandTileFeatureMethods(val self: TileFeature[MultibandTile, MTL])
    extends MethodExtensions[TileFeature[MultibandTile, MTL]] with MultibandTileFeatureMergeMethods
}
