import scala.util.Properties

import sbt._

object Dependencies {
  def either(environmentVariable: String, default: String): String =
    Properties.envOrElse(environmentVariable, default)

  private val sprayVersion = Properties.envOrElse("SPRAY_VERSION", "1.3.3")

  // Cloudera's distribution of Spark 1.5 is built with Akka 2.2.x,
  // as opposed to the official release, which is built with Akka 2.3.x.
  // We need to have the spray version match the Akka version of Spark
  // or else MethodNotFound pain will ensue.
  val sprayRouting =
    if(sprayVersion == "1.2.3") {
      "io.spray"        % "spray-routing" % sprayVersion
    } else {
      "io.spray"        %% "spray-routing" % sprayVersion
    }

  val sprayCan =
    if(sprayVersion == "1.2.3") {
      "io.spray"        % "spray-can" % sprayVersion
    } else {
      "io.spray"        %% "spray-can" % sprayVersion
    }
}
