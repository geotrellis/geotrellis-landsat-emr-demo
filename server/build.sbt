name := "server"

javaOptions += "-Xmx4G"

fork in run := true

connectInput in run := true

libraryDependencies ++= Seq(
  "com.azavea.geotrellis" %% "geotrellis-spark" % Version.geotrellis,
  "org.apache.spark" %% "spark-core" % "1.5.2",
  Dependencies.sprayRouting,
  Dependencies.sprayCan,
  "org.scalatest"       %%  "scalatest"      % "2.2.0" % "test"
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

Revolver.settings
net.virtualvoid.sbt.graph.Plugin.graphSettings
