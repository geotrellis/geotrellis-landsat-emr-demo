package demo

import geotrellis.vector._
import geotrellis.vector.io._
import geotrellis.vector.io.json._

import spray.json._
import spray.json.DefaultJsonProtocol._

//"properties": { "GEO_ID": "0400000US23", "STATE": "23", "NAME": "Maine", "LSAD": "", "CENSUSAREA": 30842.923000 }
case class StateData(name: String, area: Double)

object StateData {
  implicit object StateDataReader extends RootJsonReader[StateData] {
    def read(value: JsValue): StateData =
      value.asJsObject.getFields("NAME", "CENSUSAREA") match {
        case Seq(JsString(name), JsNumber(area)) =>
          StateData(name, area.toDouble)
        case _ =>
          throw new DeserializationException(s"StateData expected, got $value")
      }
  }
}

object States {
  def load(): Seq[MultiPolygonFeature[StateData]] = {
    val stream = getClass.getResourceAsStream("/states.json")
    val featureCollection =
      scala.io.Source.fromInputStream(stream).getLines.mkString(" ")
      .parseGeoJson[JsonFeatureCollection]
    featureCollection.getAll[MultiPolygonFeature[StateData]] ++
      featureCollection.getAll[PolygonFeature[StateData]].map(_.mapGeom { geom => MultiPolygon(geom) })
  }
}
