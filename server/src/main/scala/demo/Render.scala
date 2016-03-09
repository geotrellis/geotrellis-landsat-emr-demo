package demo

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.mapalgebra.local._

object Render {
  def fromStringDouble(str: String): Option[ColorClassifier[Double]] = {
    val split = str.split(';').map(_.trim.split(':'))
    val limits = split.map { pair => pair(0).toDouble }
    val colors = split.map { pair => BigInt(pair(1), 16).toInt }
    require(limits.size == colors.size)
    val zipped = limits.zip(colors.map(RGBA.apply))
    Some(StrictColorClassifier(zipped))
  }

  def fromStringInt(str: String): Option[ColorClassifier[Int]] = {
    val split = str.split(';').map(_.trim.split(':'))
    val limits = split.map { pair => pair(0).toInt }
    val colors = split.map { pair => BigInt(pair(1), 16).toInt }
    require(limits.size == colors.size)
    val zipped = limits.zip(colors.map(RGBA.apply))
    Some(StrictColorClassifier(zipped))
  }

  val ndviColorBreaks =
    fromStringDouble("0.05:ffffe5aa;0.1:f7fcb9ff;0.2:d9f0a3ff;0.3:addd8eff;0.4:78c679ff;0.5:41ab5dff;0.6:238443ff;0.7:006837ff;1:004529ff").get

  val ndwiColorBreaks =
    fromStringDouble("0:aacdff44;0.1:70abffff;0.2:3086ffff;0.3:1269e2ff;0.4:094aa5ff;1:012c69ff").get

  val ndviDiffColorBreaks =
    fromStringDouble("-0.6:FF4040FF;-0.5:FF5353FF;-0.4:FF6666FF;-0.3:FF7979FF;-0.2:FF8C8CFF;-0.1:FF9F9FFF;0:709AB244;0.1:81D3BBFF;0.2:67CAAEFF;0.3:4EC2A0FF;0.4:35B993FF;0.5:1CB085FF;0.6:03A878FF").get

  val waterDiffColorBreaks =
    fromStringDouble("0.2:aacdff44;0.3:1269e2ff;0.4:094aa5ff;1:012c69ff").get

  val tempDiffColorBreaks =
    fromStringInt("-30:0460FFFF;-28:1369F5FF;-26:2272ECFF;-24:327BE3FF;-22:4184DAFF;-20:508ED1FF;-18:6097C8FF;-16:6FA0BFFF;-14:7EA9B5FF;-12:8EB2ACFF;-10:9DBCA3FF;-8:ACC59AFF;-6:BCCE91FF;-4:CBD788FF;-2:DAE07FFF;0:EAEA7622;2:E9DC6EFF;4:E8CF66FF;6:E8C25EFF;8:E7B456FF;10:E7A74EFF;12:E69A46FF;14:E58D3EFF;16:E57F37FF;18:E4722FFF;20:E46527FF;22:E3581FFF;24:E24A17FF;26:E23D0FFF;28:E13007FF;30:E12300FF;1000:E12300FF").get

  // val tempDiffColorBreaks =
  //   ColorBreaks.fromStringInt("-200:0460FFFF;-18:1369F5FF;-16:2272ECFF;-14:327BE3FF;-12:4184DAFF;-10:508ED1FF;-9:6097C8FF;-16:6FA0BFFF;-14:7EA9B5FF;-21:8EB2ACFF;-10:9DBCA3FF;-8:ACC59AFF;-6:BCCE91FF;-4:CBD788FF;-2:DAE07FFF;0:EAEA7622;1:E9DC6EFF;2:E8CF66FF;3:E8C25EFF;4:E7B456FF;5:E7A74EFF;6:E69A46FF;7:E58D3EFF;8:E57F37FF;9:E4722FFF;10:E46527FF;12:E3581FFF;14:E24A17FF;16:E23D0FFF;18:E13007FF;200:E12300FF").get

  def temperature(tile: Tile, breaks: Array[Int]): Png = {
    val bcc = BlendingColorClassifier(breaks, ColorRamps.BlueToOrange)
    tile.renderPng(bcc)
  }

  def temperatureDiff(tile: Tile, breaks: Array[Int]): Png =
    tile.renderPng(tempDiffColorBreaks)

  def image(tile: MultiBandTile): Png = {
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

    ArrayMultiBandTile(adjRed, adjGreen, adjBlue).renderPng
  }

  def ndvi(tile: MultiBandTile): Png =
    NDVI(tile).renderPng(ndviColorBreaks)

  def ndvi(tile1: MultiBandTile, tile2: MultiBandTile): Png =
    (NDVI(tile1) - NDVI(tile2)).renderPng(ndviDiffColorBreaks)

  def ndwi(tile: MultiBandTile): Png =
    NDWI(tile).renderPng(ndwiColorBreaks)

  def ndwi(tile1: MultiBandTile, tile2: MultiBandTile): Png =
    (NDWI(tile1) - NDWI(tile2)).renderPng(waterDiffColorBreaks)
}
