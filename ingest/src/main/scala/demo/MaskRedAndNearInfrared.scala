// package tutorial

// import geotrellis.raster._
// import geotrellis.raster.io.geotiff._
// import geotrellis.raster.render._
// import com.typesafe.config.ConfigFactory

// object MaskRedAndNearInfrared {
//   val maskedPath = "data/r-nir.tif"

//   // Path to our landsat band geotiffs.
//   def bandPath(d: String, n: String, b: String) = s"${d}/${n}_${b}.TIF"

//   def main(args: Array[String]): Unit = {
//     val images = Seq(
//       ("/Users/rob/proj/workshops/apple/data/flooding/batesville/LC80240352015324LGN00", "batesville-2015-11-20"),
//       ("/Users/rob/proj/workshops/apple/data/flooding/batesville/LC80240352015292LGN00", "batesville-2015-10-19")
//     )

//     // Read in the red band.
//     val rGeoTiff = SingleBandGeoTiff(bandPath("B4"))

//     // Read in the near infrared band
//     val nirGeoTiff = SingleBandGeoTiff(bandPath("B5"))

//     // Read in the QA band
//     val qaGeoTiff = SingleBandGeoTiff(bandPath("BQA"))

//     // GeoTiffs have more information we need; just grab the Tile out of them.
//     val (rTile, nirTile, qaTile) = (rGeoTiff.tile, nirGeoTiff.tile, qaGeoTiff.tile)

//     // This function will set anything that is potentially a cloud to NODATA
//     def maskClouds(tile: Tile): Tile =
//       tile.combine(qaTile) { (v, qa) =>
//         val isCloud = qa & 0x8000
//         val isCirrus = qa & 0x2000
//         if(isCloud > 0 || isCirrus > 0) { NODATA }
//         else { v }
//       }

//     // Mask our red and near infrared bands using the qa band
//     val rMasked = maskClouds(rTile)
//     val nirMasked = maskClouds(nirTile)

//     // Create a multiband tile with our two masked red and infrared bands.
//     val mb = ArrayMultiBandTile(rMasked, nirMasked).convert(TypeInt)

//     // Create a multiband geotiff from our tile, using the same extent and CRS as the original geotiffs.
//     MultiBandGeoTiff(mb, rGeoTiff.extent, rGeoTiff.crs).write(maskedPath)
//   }
// }
