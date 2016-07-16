package demo.merge

import com.azavea.landsatutil.MTL
import geotrellis.raster._
import geotrellis.raster.merge._
import geotrellis.raster.resample._
import geotrellis.vector.Extent
import spire.syntax.cfor._

/**
  * Trait containing extension methods for doing merge operations on
  * single-band [[TileFeature[Tile, MTL]]]s.
  */
trait SinglebandTileFeatureMergeMethods extends TileMergeMethods[TileFeature[Tile, MTL]] {
  /** Merges this tile with another tile.
    *
    * This method will replace the values of these cells with the
    * values of the other tile's corresponding cells, if the source
    * cell is of the transparent value.  The transparent value is
    * determined by the tile's cell type; if the cell type has a
    * NoData value, then that is considered the transparent value.  If
    * there is no NoData value associated with the cell type, then a 0
    * value is considered the transparent value. If this is not the
    * desired effect, the caller is required to change the cell type
    * before using this method to an appropriate cell type that has
    * the desired NoData value.
    *
    * @note                         This method requires that the dimensions be the same between the tiles, and assumes
    *                               equal extents.
    */
  def merge(other: TileFeature[Tile, MTL]): TileFeature[Tile, MTL] = {
    val mutableTile = self.tile.mutable
    Seq(self.tile, other.tile).assertEqualDimensions()
    self.cellType match {
      case BitCellType =>
        cfor(0)(_ < self.rows, _ + 1) { row =>
          cfor(0)(_ < self.cols, _ + 1) { col =>
            if (other.tile.get(col, row) == 1) {
              mutableTile.set(col, row, 1)
            }
          }
        }
      case ByteCellType | UByteCellType | ShortCellType | UShortCellType | IntCellType  =>
        // Assume 0 as the transparent value
        cfor(0)(_ < self.rows, _ + 1) { row =>
          cfor(0)(_ < self.cols, _ + 1) { col =>
            if (self.tile.get(col, row) == 0) {
              mutableTile.set(col, row, other.tile.get(col, row))
            }
          }
        }
      case FloatCellType | DoubleCellType =>
        // Assume 0.0 as the transparent value
        cfor(0)(_ < self.rows, _ + 1) { row =>
          cfor(0)(_ < self.cols, _ + 1) { col =>
            if (self.tile.getDouble(col, row) == 0.0) {
              mutableTile.setDouble(col, row, other.tile.getDouble(col, row))
            }
          }
        }
      case x if x.isFloatingPoint =>
        cfor(0)(_ < self.rows, _ + 1) { row =>
          cfor(0)(_ < self.cols, _ + 1) { col =>
            if (isNoData(self.tile.getDouble(col, row))) {
              mutableTile.setDouble(col, row, other.tile.getDouble(col, row))
            }
          }
        }
      case _ =>
        cfor(0)(_ < self.rows, _ + 1) { row =>
          cfor(0)(_ < self.cols, _ + 1) { col =>
            if (isNoData(self.tile.get(col, row))) {
              mutableTile.set(col, row, other.tile.get(col, row))
            }
          }
        }
    }

    TileFeature(mutableTile, other.data)
  }

  /** Merges this tile with another tile, given the extents both tiles.
    *
    * This method will replace the values of these cells with a
    * resampled value taken from the tile's cells, if the source cell
    * is of the transparent value.  The transparent value is
    * determined by the tile's cell type; if the cell type has a
    * NoData value, then that is considered the transparent value.  If
    * there is no NoData value associated with the cell type, then a 0
    * value is considered the transparent value. If this is not the
    * desired effect, the caller is required to change the cell type
    * before using this method to an appropriate cell type that has
    * the desired NoData value.
    */
  def merge(extent: Extent, otherExtent: Extent, other: TileFeature[Tile, MTL], method: ResampleMethod): TileFeature[Tile, MTL] =
    otherExtent & extent match {
      case Some(sharedExtent) =>
        val mutableTile = self.tile.mutable
        val re = RasterExtent(extent, self.cols, self.rows)
        val GridBounds(colMin, rowMin, colMax, rowMax) = re.gridBoundsFor(sharedExtent)
        val targetCS = CellSize(sharedExtent, colMax, rowMax)

        self.cellType match {
          case BitCellType | ByteCellType | UByteCellType | ShortCellType | UShortCellType | IntCellType  =>
            val interpolate = Resample(method, other.tile, otherExtent, targetCS).resample _
            // Assume 0 as the transparent value
            cfor(0)(_ < self.rows, _ + 1) { row =>
              cfor(0)(_ < self.cols, _ + 1) { col =>
                if (self.tile.get(col, row) == 0) {
                  val (x, y) = re.gridToMap(col, row)
                  mutableTile.set(col, row, interpolate(x, y))
                }
              }
            }
          case FloatCellType | DoubleCellType =>
            val interpolate = Resample(method, other.tile, otherExtent, targetCS).resampleDouble _
            // Assume 0.0 as the transparent value
            cfor(0)(_ < self.rows, _ + 1) { row =>
              cfor(0)(_ < self.cols, _ + 1) { col =>
                if (self.tile.getDouble(col, row) == 0.0) {
                  val (x, y) = re.gridToMap(col, row)
                  mutableTile.setDouble(col, row, interpolate(x, y))
                }
              }
            }
          case x if x.isFloatingPoint =>
            val interpolate = Resample(method, other.tile, otherExtent, targetCS).resampleDouble _
            cfor(0)(_ < self.rows, _ + 1) { row =>
              cfor(0)(_ < self.cols, _ + 1) { col =>
                if (isNoData(self.tile.getDouble(col, row))) {
                  val (x, y) = re.gridToMap(col, row)
                  mutableTile.setDouble(col, row, interpolate(x, y))
                }
              }
            }
          case _ =>
            val interpolate = Resample(method, other.tile, otherExtent, targetCS).resample _
            cfor(0)(_ < self.rows, _ + 1) { row =>
              cfor(0)(_ < self.cols, _ + 1) { col =>
                if (isNoData(self.tile.get(col, row))) {
                  val (x, y) = re.gridToMap(col, row)
                  mutableTile.set(col, row, interpolate(x, y))
                }
              }
            }
        }

        TileFeature(mutableTile, other.data)
      case _ =>
        self
    }
}
