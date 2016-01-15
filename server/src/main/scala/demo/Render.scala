package demo

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.op.local._

object Render {
  val ndviColorBreaks =
    ColorBreaks.fromStringDouble("0:ffffe5ff;0.1:f7fcb9ff;0.2:d9f0a3ff;0.3:addd8eff;0.4:78c679ff;0.5:41ab5dff;0.6:238443ff;0.7:006837ff;1:004529ff").get

  val ndwiColorBreaks =
    ColorBreaks.fromStringDouble("0.1:aacdffaa;0.2:70abffff;0.4:3086ffff;0.6:1269e2ff;0.8:094aa5ff;1:012c69ff").get

  def temperature(tile: Tile, breaks: Array[Int]): Png =
    tile.renderPng(ColorRamps.BlueToOrange, breaks)

  def tempuratureDiff(tile: Tile, breaks: Array[Int]): Png = {
    //if(layer == "diff") tile.renderPng(ColorRamps.LightToDarkGreen, breaks.split(",").map(_.toInt)).bytes
    ???

  }

  def image(tile: MultiBandTile, breaks: Array[Int]): Png = {
                // val tile = tileReader.read(LayerId(layer, zoom), SpaceTimeKey(x, y, time))
                // val red = tile.band(0).convert(TypeInt).map(z => if(z == 0) NODATA else z).color(ColorBreaks(totalBreaks, (0 until 256).toArray))
                // val green = tile.band(1).convert(TypeInt).map(z => if(z == 0) NODATA else z).color(ColorBreaks(totalBreaks, (0 until 256).toArray))
                // val blue = tile.band(2).convert(TypeInt).map(z => if(z == 0) NODATA else z).color(ColorBreaks(totalBreaks, (0 until 256).toArray))
                // ArrayMultiBandTile(red, green, blue).renderPng.bytes

                // val tile = tileReader.read(LayerId(layer, zoom), SpaceTimeKey(x, y, time))
                // val red = tile.band(0).convert(TypeInt).map(z => if(z == 0) NODATA else z).color(ColorBreaks(redBreaks, (0 until 256).toArray))
                // val green = tile.band(1).convert(TypeInt).map(z => if(z == 0) NODATA else z).color(ColorBreaks(greenBreaks, (0 until 256).toArray))
                // val blue = tile.band(2).convert(TypeInt).map(z => if(z == 0) NODATA else z).color(ColorBreaks(blueBreaks, (0 until 256).toArray))
                // ArrayMultiBandTile(red, green, blue).renderPng.bytes
    val (min, max) = (4000, 15176)
    val red = tile.band(0).convert(TypeInt).map(z => if(z == 0) NODATA else z).normalize(min, max, 0, 255)
    val green = tile.band(1).convert(TypeInt).map(z => if(z == 0) NODATA else z).normalize(min, max, 0, 255)
    val blue = tile.band(2).convert(TypeInt).map(z => if(z == 0) NODATA else z).normalize(min, max, 0, 255)
    ArrayMultiBandTile(red, green, blue).renderPng.bytes
  }

  def ndvi(tile: MultiBandTile, breaks: Array[Int]): Png =
    NDVI(tile).renderPng(ndviColorBreaks)

  def ndvi(tile1: MultiBandTile, tile2: MultiBandTile, breaks: Array[Int]): Png = {
    val ramp = ColorRamp(ndviColorBreaks.colors)
    val cb = ColorBreaks(breaks, ramp.interpolate(breaks.length).colors.toArray)

    ((NDVI(tile1) * 1000.0) - (NDVI(tile2) * 1000.0)).renderPng(cb)
  }

  def ndwi(tile: MultiBandTile, breaks: Array[Int]): Png =
    NDWI(tile).renderPng(ndwiColorBreaks)

  def ndwi(tile1: MultiBandTile, tile2: MultiBandTile, breaks: Array[Int]): Png = {
    val ramp = ColorRamp(ndwiColorBreaks.colors)
    val cb = ColorBreaks(breaks, ramp.interpolate(breaks.length).colors.toArray)

    ((NDWI(tile1) * 1000.0) - (NDWI(tile2) * 1000.0)).renderPng(cb)
  }

}
