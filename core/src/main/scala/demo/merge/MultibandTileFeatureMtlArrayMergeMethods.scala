package demo.merge

import com.azavea.landsatutil.MTL
import geotrellis.raster._
import geotrellis.raster.merge._
import geotrellis.raster.resample._
import geotrellis.vector.Extent

/**
  * A trait containing extension methods related to TileFeature[MultibandTile, MtlArray]
  * merging.
  */
trait MultibandTileFeatureMtlArrayMergeMethods extends TileMergeMethods[TileFeature[MultibandTile, Array[MTL]]] {
  /**
    * Merge the respective bands of this MultibandTile and the other
    * one.
    */
  def merge(other: TileFeature[MultibandTile, Array[MTL]]): TileFeature[MultibandTile, Array[MTL]] = {
    val mutableData = self.data
    for {
      bandIndex <- 0 until self.tile.bandCount
    } yield {
      val thisBand = TileFeature(self.tile.band(bandIndex), mutableData)
      val thatBand = TileFeature(other.tile.band(bandIndex), mutableData)
      thisBand.merge(thatBand)
    }
  }

  /**
    * Merge this [[TileFeature[MultibandTile, Array[MTL]]]] with the other one. All places in
    * the present tile that contain NODATA and are in the intersection
    * of the two given extents are filled-in with data from the other
    * tile. A new TileFeature[MultibandTile, Array[MTL]] is returned.
    *
    * @param   extent        The extent of this MultiBandTile
    * @param   otherExtent   The extent of the other MultiBandTile
    * @param   other         The other TileFeature[MultibandTile, Array[MTL]]
    * @param   method        The resampling method
    * @return                A new MultiBandTile, the result of the merge
    */
  def merge(extent: Extent, otherExtent: Extent, other: TileFeature[MultibandTile, Array[MTL]], method: ResampleMethod): TileFeature[MultibandTile, Array[MTL]] = {
    val mutableData = self.data
    for {
      bandIndex <- 0 until self.tile.bandCount
    } yield {
      val thisBand = TileFeature(self.tile.band(bandIndex), mutableData)
      val thatBand = TileFeature(other.tile.band(bandIndex), mutableData)
      thisBand.merge(extent, otherExtent, thatBand, method)
    }
  }
}
