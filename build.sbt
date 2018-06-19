import sbt.Keys.{publishArtifact, _}
import sbt._

val scalaBinaryVersionNumber = "2.12"
val scalaVersionNumber = s"$scalaBinaryVersionNumber.4"

resolvers += Resolver.bintrayIvyRepo("sbt", "sbt-plugin-releases")

lazy val aggregatedProjects: Seq[ProjectReference] = Seq(codacyAnalysisCore, codacyAnalysisCli)

lazy val root = project
  .in(file("."))
  .settings(name := "root")
  .settings(inThisBuild(List(
    organization := "com.codacy",
    scalacOptions ++= Common.compilerFlags,
    scalacOptions in Test ++= Seq("-Yrangepos"),
    scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings"))))
  .aggregate(aggregatedProjects: _*)
  .settings(publish := {}, publishLocal := {}, publishArtifact := false)

lazy val codacyAnalysisCore = project
  .in(file("core"))
  .settings(
    inThisBuild(List(scalaVersion := scalaVersionNumber, version := "0.1.0-SNAPSHOT")),
    publishMavenStyle := true,
    name := "codacy-analysis-core",
    publishArtifact in Test := false,
    pomIncludeRepository := { _ =>
      false
    },
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (version.value.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    // App Dependencies
    libraryDependencies ++= Seq(
      Dependencies.caseApp,
      Dependencies.betterFiles,
      Dependencies.jodaTime,
      Dependencies.codacyPlugins,
      Dependencies.fansi,
      Dependencies.scalajHttp,
      Dependencies.cats) ++
      Dependencies.circe ++
      Dependencies.jackson ++
      Dependencies.log4s)
  .settings(libraryDependencies ++= Dependencies.specs2.map(_ % Test))

lazy val codacyAnalysisCli = project
  .in(file("cli"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(
    inThisBuild(List(scalaVersion := scalaVersionNumber, version := "0.1.0-SNAPSHOT")),
    name := "codacy-analysis-cli")
  .settings(Common.dockerSettings: _*)
  .settings(Common.genericSettings: _*)
  .settings(publish := {}, publishLocal := {}, publishArtifact := false)
  .settings(libraryDependencies ++= Dependencies.specs2.map(_ % Test))
  .dependsOn(codacyAnalysisCore)
  .aggregate(codacyAnalysisCore)

// Scapegoat
scalaVersion in ThisBuild := scalaVersionNumber
scalaBinaryVersion in ThisBuild := scalaBinaryVersionNumber
scapegoatDisabledInspections in ThisBuild := Seq()
scapegoatVersion in ThisBuild := "1.3.4"
compile.in(Compile) := Def.taskDyn {
  val c = compile.in(Compile).value
  Def.task {
    if (sys.env.get("CI").exists(_.nonEmpty)) Def.taskDyn(Def.task(scapegoat.in(Compile).value))
    c
  }
}.value

compile.in(Test) := Def.taskDyn {
  val c = compile.in(Test).value
  Def.task {
    if (sys.env.get("CI").exists(_.nonEmpty)) Def.taskDyn(Def.task(scapegoat.in(Compile).value))
    c
  }
}.value
