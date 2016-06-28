package demo.etl.landsat

import geotrellis.spark.etl.TypedModule

object LandsatModule extends TypedModule {
  register(new TemporalMultibandLandsatInput)
}
