package demo

import com.azavea.landsatutil.MTL
import geotrellis.raster._
import geotrellis.util._

package object merge extends Serializable {
  implicit class withSinglebandTileFeatureMethods(val self: TileFeature[Tile, MTL])
    extends MethodExtensions[TileFeature[Tile, MTL]] with SinglebandTileFeatureMergeMethods

  implicit class withMultibandTileFeatureMethods(val self: TileFeature[MultibandTile, MTL])
    extends MethodExtensions[TileFeature[MultibandTile, MTL]] with MultibandTileFeatureMergeMethods

  implicit class withSinglebandTileFeatureMtlArrayMethods(val self: TileFeature[Tile, Array[MTL]])
    extends MethodExtensions[TileFeature[Tile, Array[MTL]]] with SinglebandTileFeatureMtlArrayMergeMethods

  implicit class withMultibandTileFeatureMtlArrayMethods(val self: TileFeature[MultibandTile, Array[MTL]])
    extends MethodExtensions[TileFeature[MultibandTile, Array[MTL]]] with MultibandTileFeatureMtlArrayMergeMethods

  implicit def tileFeatureToMultibandTileFeature(seq: Seq[TileFeature[Tile, Array[MTL]]]): TileFeature[MultibandTile, Array[MTL]] =
    TileFeature(ArrayMultibandTile(seq.map(_.tile)), seq.head.data)
}
