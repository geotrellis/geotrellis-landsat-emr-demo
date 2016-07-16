package demo.merge

import com.azavea.landsatutil.MTL
import geotrellis.raster._
import geotrellis.raster.merge._
import geotrellis.raster.resample._
import geotrellis.vector.Extent
import spire.syntax.cfor._

/**
  * A trait containing extension methods related to TileFeature[MultibandTile, MTL]
  * merging.
  */
trait MultibandTileFeatureMergeMethods extends TileMergeMethods[TileFeature[MultibandTile, MTL]] {
  /**
    * Merge the respective bands of this MultibandTile and the other
    * one.
    */
  def merge(other: TileFeature[MultibandTile, MTL]): TileFeature[MultibandTile, MTL] = {
    val bands: Seq[Tile] =
      for {
        bandIndex <- 0 until self.tile.bandCount
      } yield {
        val thisBand = self.tile.band(bandIndex)
        val thatBand = other.tile.band(bandIndex)
        thisBand.merge(thatBand)
      }

    TileFeature(ArrayMultibandTile(bands), other.data)
  }

  /**
    * Merge this [[TileFeature[MultibandTile, MTL]]] with the other one. All places in
    * the present tile that contain NODATA and are in the intersection
    * of the two given extents are filled-in with data from the other
    * tile. A new TileFeature[MultibandTile, MTL] is returned.
    *
    * @param   extent        The extent of this MultiBandTile
    * @param   otherExtent   The extent of the other MultiBandTile
    * @param   other         The other TileFeature[MultibandTile, MTL]
    * @param   method        The resampling method
    * @return                A new MultiBandTile, the result of the merge
    */
  def merge(extent: Extent, otherExtent: Extent, other: TileFeature[MultibandTile, MTL], method: ResampleMethod): TileFeature[MultibandTile, MTL] = {
    val bands: Seq[Tile] =
      for {
        bandIndex <- 0 until self.tile.bandCount
      } yield {
        val thisBand = self.tile.band(bandIndex)
        val thatBand = other.tile.band(bandIndex)
        thisBand.merge(extent, otherExtent, thatBand, method)
      }

    TileFeature(ArrayMultibandTile(bands), other.data)
  }
}
