import sbt.Keys._
import sbt._

val scalaBinaryVersionNumber = "2.12"
val scalaVersionNumber = s"$scalaBinaryVersionNumber.4"

resolvers += Resolver.bintrayIvyRepo("sbt", "sbt-plugin-releases")

lazy val codacyAnalysisCli = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(
    inThisBuild(
      List(
        organization := "com.codacy",
        scalaVersion := scalaVersionNumber,
        version := "0.1.0-SNAPSHOT",
        scalacOptions ++= Common.compilerFlags,
        scalacOptions in Test ++= Seq("-Yrangepos"),
        scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings"))),
    name := "codacy-analysis-cli",
    // App Dependencies
    libraryDependencies ++= Seq(
      Dependencies.caseApp,
      Dependencies.betterFiles,
      Dependencies.jodaTime,
      Dependencies.fansi,
      Dependencies.scalajHttp,
      Dependencies.cats) ++
      Dependencies.circe ++
      Dependencies.jackson ++
      Dependencies.log4s ++
      Dependencies.codacyPlugins,
    // Test Dependencies
    libraryDependencies ++= Dependencies.specs2.map(_ % Test))
  .settings(Common.dockerSettings: _*)
  .settings(Common.genericSettings: _*)

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
