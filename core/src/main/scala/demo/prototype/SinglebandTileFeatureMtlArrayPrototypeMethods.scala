package demo.prototype

import com.azavea.landsatutil.MTL
import geotrellis.raster._
import geotrellis.raster.prototype.TilePrototypeMethods


/**
  * Trait containing prototype methods for single-band [[TileFeature[Tile, MTL]]]s.
  */
trait SinglebandTileFeatureMtlArrayPrototypeMethods extends TilePrototypeMethods[TileFeature[Tile, Array[MTL]]] {

  /**
    * Given a [[CellType]] and numbers of columns and rows, produce a
    * new [[ArrayTile]] of the given size and the same band count as
    * the calling object.
    */

  def prototype(cellType: CellType, cols: Int, rows: Int) =
    TileFeature(ArrayTile.empty(cellType, cols, rows), Array.fill[MTL](cols * rows + cols) { new MTL(Map()) })

  /**
    * Given numbers of columns and rows, produce a new [[ArrayTile]]
    * of the given size and the same band count as the calling object.
    */
  def prototype(cols: Int, rows: Int) =
    prototype(self.cellType, cols, rows)
}
