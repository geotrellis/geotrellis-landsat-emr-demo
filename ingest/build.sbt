name := "ingest"
scalaVersion := Version.scala
javaOptions += "-Xmx8G"

fork in run := true

connectInput in run := true

libraryDependencies ++= Seq(
  "com.azavea" %% "scala-landsat-util" % "1.0.0",
  "org.locationtech.geotrellis" %% "geotrellis-spark-etl" % Version.geotrellis,
  "org.apache.spark" %% "spark-core" % "2.1.0" % "provided",
  "org.locationtech.geotrellis" %% "geotrellis-spark-testkit" % Version.geotrellis % "test",
  "org.scalatest" %%  "scalatest" % "3.0.1" % "test"
)

assemblyShadeRules in assembly := {
  val shadePackage = "com.azavea.shaded.demo"
  Seq(
    ShadeRule.rename("com.google.common.**" -> s"$shadePackage.google.common.@1")
      .inLibrary(
      "com.azavea.geotrellis" %% "geotrellis-cassandra" % Version.geotrellis,
        "com.github.fge" % "json-schema-validator" % "2.2.6"
    ).inAll
  )
}

test in assembly := {}

assemblyMergeStrategy in assembly := {
  case m if m.toLowerCase.endsWith("manifest.mf") => MergeStrategy.discard
  case m if m.toLowerCase.matches("meta-inf.*\\.sf$") => MergeStrategy.discard
  case "reference.conf" | "application.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}

