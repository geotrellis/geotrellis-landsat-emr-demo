name := "core"

libraryDependencies ++= Seq(
  "com.azavea.geotrellis" %% "geotrellis-spark" % Version.geotrellis,
  "org.apache.spark" %% "spark-core" % "1.5.2",
  "org.scalatest"       %%  "scalatest"      % "2.2.0" % "test"
)

net.virtualvoid.sbt.graph.Plugin.graphSettings
