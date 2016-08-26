name := "ingest"
scalaVersion := Version.scala
javaOptions += "-Xmx8G"

fork in run := true

connectInput in run := true

libraryDependencies ++= Seq(
  "com.azavea" %% "scala-landsat-util" % "0.2.1-b1610a6",
  "com.azavea.geotrellis" %% "geotrellis-spark-etl" % Version.geotrellis,
  "org.apache.spark"      %% "spark-core" % "1.5.2" % "provided",
  "com.azavea.geotrellis" %% "geotrellis-spark-testkit" % Version.geotrellis % "test",
  "org.scalatest"         %%  "scalatest" % "2.2.0" % "test"
)

assemblyMergeStrategy in assembly := {
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case "META-INF\\MANIFEST.MF" => MergeStrategy.discard
  case "META-INF/ECLIPSEF.RSA" => MergeStrategy.discard
  case "META-INF/ECLIPSEF.SF" => MergeStrategy.discard
  case _ => MergeStrategy.first
}

assemblyShadeRules in assembly := {
  val shadePackage = "com.azavea.shaded.demo"
  Seq(ShadeRule.rename("com.google.common.**" -> s"$shadePackage.google.common.@1").inAll)
}
