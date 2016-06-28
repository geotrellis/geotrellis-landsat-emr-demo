package demo.etl.landsat

import demo.LandsatIngestMain._
import geotrellis.raster.MultibandTile
import geotrellis.spark.TemporalProjectedExtent
import geotrellis.spark.etl.EtlJob
import geotrellis.vector.Extent

import com.azavea.landsatutil.Landsat8Query
import org.joda.time.LocalDate
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

class TemporalMultibandLandsatInput extends LandsatInput[TemporalProjectedExtent, MultibandTile] {
  val format = "temporal-landsat"

  def apply(job: EtlJob)(implicit sc: SparkContext): RDD[(TemporalProjectedExtent, MultibandTile)] = {
    val input = job.landsatInput

    val images = Landsat8Query()
      .withStartDate(input.get('startDate).map(LocalDate.parse).getOrElse(new LocalDate(2014,1,1)).toDateTimeAtStartOfDay)
      .withEndDate(input.get('endDate).map(LocalDate.parse).getOrElse(new LocalDate(2015,1,1)).toDateTimeAtStartOfDay)
      .withMaxCloudCoverage(input.get('maxCloudCoverage).map(_.toDouble).getOrElse(100d))
      .intersects(Extent.fromString(input('extent)))
      .collect()

    logger.info(s"Found ${images.length} landsat images")

    this.images = input.get('limit).fold(images)(limit => images.take(limit.toInt))

    fetch(job, this.images, fetchMethod)
  }
}