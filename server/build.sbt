name := "server"
scalaVersion := Version.scala
javaOptions += "-Xmx4G"

fork in run := true

connectInput in run := true

libraryDependencies ++= Seq(
  "org.locationtech.geotrellis" %% "geotrellis-spark" % Version.geotrellis,
  "org.locationtech.geotrellis" %% "geotrellis-s3" % Version.geotrellis,
  "org.locationtech.geotrellis" %% "geotrellis-accumulo" % Version.geotrellis,
  "org.locationtech.geotrellis" %% "geotrellis-hbase" % Version.geotrellis,
  "org.locationtech.geotrellis" %% "geotrellis-cassandra" % Version.geotrellis,
  "org.apache.spark" %% "spark-core" % "2.1.0" % "provided",
  Dependencies.akkaHttp,
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.3",
  "ch.megard" %% "akka-http-cors" % "0.1.11",
  "org.scalatest" %%  "scalatest" % "3.0.1" % "test"
)

Revolver.settings

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
