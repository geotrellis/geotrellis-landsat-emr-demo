package demo

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.mapalgebra.local._

object Render {

  val ndviColorBreaks = {
    val xs = List(0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 1.0)
    val colors = List("ffffe5aa", "f7fcb9ff", "d9f0a3ff", "addd8eff", "78c679ff", "ab5dff", "238443ff", "006837ff", "004529ff").map({ s => RGBA(Integer.parseInt(s, 16)) })
    xs.zip(colors).toArray
  }

  val ndwiColorBreaks = {
    val xs = List(0, 0.1, 0.2, 0.3, 0.4, 1.0)
    val colors = List("aacdff44", "70abffff", "3086ffff", "1269e2ff", "094aa5ff", "012c69ff").map({ s => RGBA(Integer.parseInt(s, 16)) })
    xs.zip(colors).toArray
  }

  val ndviDiffColorBreaks = {
    val xs = List(-0.6, -0.5, -0.4, -0.3, -0.2, -0.1, 0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6)
    val colors = List("FF4040FF", "FF5353FF", "FF6666FF", "FF7979FF", "FF8C8CFF", "FF9F9FFF", "709AB244", "81D3BBFF", "67CAAEFF", "4EC2A0FF", "35B993FF", "1CB085FF", "03A878FF").map({ s => RGBA(Integer.parseInt(s, 16)) })
    xs.zip(colors).toArray
  }

  val waterDiffColorBreaks = {
    val xs = List(0.2, 0.3, 0.4, 1.0)
    val colors = List("aacdff44", "1269e2ff", "094aa5ff", "012c69ff").map({ s => RGBA(Integer.parseInt(s, 16)) })
    xs.zip(colors).toArray
  }

  val tempDiffColorBreaks = {
    val xs = List(-30.0, -28.0, -26.0, -24.0, -22.0, -20.0, -18.0, -16.0, -14.0, -12.0, -10.0, -8.0, -6.0, -4.0, -2.0, 0.0, 2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 24.0, 26.0, 28.0, 30.0, 1000.0)
    val colors = List("0460FFFF", "1369F5FF", "2272ECFF", "327BE3FF", "4184DAFF", "508ED1FF", "6097C8FF", "6FA0BFFF", "7EA9B5FF", "8EB2ACFF", "9DBCA3FF", "ACC59AFF", "BCCE91FF", "CBD788FF", "DAE07FFF", "EAEA7622", "E9DC6EFF", "E8CF66FF", "E8C25EFF", "E7B456FF", "E7A74EFF", "E69A46FF", "E58D3EFF", "E57F37FF", "E4722FFF", "E46527FF", "E3581FFF", "E24A17FF", "E23D0FFF", "E13007FF", "E12300FF", "E12300FF").map({ s => RGBA(Integer.parseInt(s, 16)) })
    xs.zip(colors).toArray
  }

  def temperature(tile: Tile, breaks: Array[Int]): Png =
    tile.renderPng(StrictColorClassifier(breaks.zip(ColorRamps.BlueToOrange)))

  def temperatureDiff(tile: Tile, breaks: Array[Int]): Png =
    tile.renderPng(StrictColorClassifier(tempDiffColorBreaks))

  def image(tile: MultibandTile): Png = {
    val (red, green, blue) =
    if(tile.cellType == UShortCellType) {
      // Landsat

      // magic numbers. Fiddled with until visually it looked ok. ¯\_(ツ)_/¯
      val (min, max) = (4000, 15176)

      def clamp(z: Int) = {
        if(isData(z)) { if(z > max) { max } else if(z < min) { min } else { z } }
        else { z }
      }
      val red = tile.band(0).convert(IntCellType).map(clamp _).normalize(min, max, 0, 255)
      val green = tile.band(1).convert(IntCellType).map(clamp _).normalize(min, max, 0, 255)
      val blue = tile.band(2).convert(IntCellType).map(clamp _).normalize(min, max, 0, 255)
      (red, green, blue)
    } else {
      // Planet Labs
      (tile.band(0).combine(tile.band(3)) { (z, m) => if(m == 0) 0 else z },
       tile.band(1).combine(tile.band(3)) { (z, m) => if(m == 0) 0 else z },
       tile.band(2).combine(tile.band(3)) { (z, m) => if(m == 0) 0 else z })
    }


    def clampColor(c: Int): Int =
      if(isNoData(c)) { c }
      else {
        if(c < 0) { 0 }
        else if(c > 255) { 255 }
        else c
      }

    // -255 to 255
    val brightness = 15
    def brightnessCorrect(v: Int): Int =
      if(v > 0) { v + brightness }
      else { v }

    // 0.01 to 7.99
    val gamma = 0.8
    val gammaCorrection = 1 / gamma
    def gammaCorrect(v: Int): Int =
      (255 * math.pow(v / 255.0, gammaCorrection)).toInt

    // -255 to 255
    val contrast: Double = 30.0
    val contrastFactor = (259 * (contrast + 255)) / (255 * (259 - contrast))
    def contrastCorrect(v: Int): Int =
      ((contrastFactor * (v - 128)) + 128).toInt

    def adjust(c: Int): Int = {
      if(isData(c)) {
        var cc = c
        cc = clampColor(brightnessCorrect(cc))
        cc = clampColor(gammaCorrect(cc))
        cc = clampColor(contrastCorrect(cc))
        cc
      } else {
        c
      }
    }

    val adjRed = red.map(adjust _)
    val adjGreen = green.map(adjust _)
    val adjBlue = blue.map(adjust _)

    ArrayMultibandTile(adjRed, adjGreen, adjBlue).renderPng
  }

  def ndvi(tile: MultibandTile): Png =
    NDVI(tile).renderPng(StrictColorClassifier(ndviColorBreaks))

  def ndvi(tile1: MultibandTile, tile2: MultibandTile): Png =
    (NDVI(tile1) - NDVI(tile2)).renderPng(StrictColorClassifier(ndviDiffColorBreaks))

  def ndwi(tile: MultibandTile): Png =
    NDWI(tile).renderPng(StrictColorClassifier(ndwiColorBreaks))

  def ndwi(tile1: MultibandTile, tile2: MultibandTile): Png =
    (NDWI(tile1) - NDWI(tile2)).renderPng(StrictColorClassifier(waterDiffColorBreaks))
}
