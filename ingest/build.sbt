name := "ingest"
scalaVersion := Version.scala
javaOptions += "-Xmx8G"

fork in run := true

connectInput in run := true

libraryDependencies ++= Seq(
  "com.azavea" %% "scala-landsat-util" % "0.2.0-SNAPSHOT",
  "com.azavea.geotrellis" %% "geotrellis-spark-etl" % Version.geotrellis
    exclude("com.datastax.cassandra", "cassandra-driver-core")
    exclude("com.github.fge", "json-schema-validator"),
  "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.10.2"
    excludeAll (ExclusionRule("org.jboss.netty"), ExclusionRule("io.netty"), ExclusionRule("org.slf4j"), ExclusionRule("io.spray"), ExclusionRule("com.typesafe.akka"))
    exclude("org.apache.hadoop", "hadoop-client"),
  "com.github.fge"         % "json-schema-validator" % "2.1.10",
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
