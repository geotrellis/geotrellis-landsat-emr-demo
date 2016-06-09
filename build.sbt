scalaVersion := Version.scala

lazy val commonSettings = Seq(
  version := "0.1.0",
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

  resolvers ++= Seq(
    Resolver.bintrayRepo("azavea", "geotrellis"),
    Resolver.bintrayRepo("azavea", "maven")),

  shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
) ++ net.virtualvoid.sbt.graph.Plugin.graphSettings

lazy val root = Project("demo", file("."))
  .aggregate(ingest, server)

lazy val ingest = Project("ingest", file("ingest"))
  .settings(commonSettings: _*)

lazy val server = Project("server", file("server"))
  .settings(commonSettings: _*)
