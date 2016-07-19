package demo.prototype

import com.azavea.landsatutil.MTL
import geotrellis.raster._
import geotrellis.raster.prototype._

/**
  * Trait containing prototype methods for [[TileFeature[MultibandTile, MTL]]]s.
  */
trait MultibandTileFeatureMtlArrayPrototypeMethods extends TilePrototypeMethods[TileFeature[MultibandTile, Array[MTL]]] {

  /**
    * Given a [[CellType]] and numbers of columns and rows, produce a
    * new [[ArrayMultibandTile]] of the given size and the same band
    * count as the calling object.
    */
  def prototype(cellType: CellType, cols: Int, rows: Int) =
    TileFeature(ArrayMultibandTile.empty(cellType, self.tile.bandCount, cols, rows), Array.fill[MTL](cols * rows + cols) { new MTL(Map()) })

  /**
    * Given numbers of columns and rows, produce a new
    * [[ArrayMultibandTile]] of the given size and the same band count
    * as the calling object.
    */
  def prototype(cols: Int, rows: Int) =
    prototype(self.cellType, cols, rows)
}
