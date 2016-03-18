val deps = Seq(
  "com.azavea.geotrellis" %% "geotrellis-spark" % Version.geotrellis,
  "com.azavea.geotrellis" %% "geotrellis-accumulo" % Version.geotrellis,
  "com.azavea.geotrellis" %% "geotrellis-s3" % Version.geotrellis
)

lazy val commonSettings = Seq(
  version := "0.1.0",
  scalaVersion := "2.10.5",
  crossScalaVersions := Seq("2.11.5", "2.10.5"),
  organization := "com.azavea",
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-Yinline-warnings",
    "-language:implicitConversions",
    "-language:reflectiveCalls",
    "-language:higherKinds",
    "-language:postfixOps",
    "-language:existentials",
    "-feature"),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },

  resolvers += Resolver.bintrayRepo("azavea", "geotrellis"),

  shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
) ++ net.virtualvoid.sbt.graph.Plugin.graphSettings

lazy val root = Project("demo", file("."))
  .aggregate(ingest, server)

lazy val ingest = Project("ingest", file("ingest"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= deps)

lazy val server = Project("server", file("server"))
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= deps)
