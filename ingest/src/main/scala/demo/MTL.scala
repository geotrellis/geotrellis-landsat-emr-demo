package demo

import com.github.nscala_time.time.Imports._
import org.joda.time.format.ISODateTimeFormat

case class MTL(dateTime: DateTime)

object MTL {
  def read(path: String): String =
    scala.io.Source.fromFile(path, "UTF-8").getLines.mkString

  def apply(path: String): MTL = {
    // Hack the date out of the text
    val dateTime =
      scala.io.Source.fromFile(path, "UTF-8")
        .getLines
        .filter { l => l.contains("FILE_DATE =") }
        .map { txt =>
          val iso = txt.replace("FILE_DATE = ", "").trim
          DateTime.parse(iso, DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ"))
        }
        .toList
        .head

    MTL(dateTime)
  }
}
