import sbt.Keys._
import sbt._

//ceenasas
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
        scalacOptions in(Compile, console) --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings"))),
    name := "codacy-analysis-cli",
    // App Dependencies
    libraryDependencies ++= Seq(
      Dependencies.caseApp,
      Dependencies.betterFiles,
      Dependencies.jodaTime) ++
      Dependencies.circe ++
      Dependencies.log4s,
    // Test Dependencies
    libraryDependencies ++= Seq(Dependencies.specs2).map(_ % Test))
  .settings(Common.dockerSettings: _*)

// javaOptions in Universal ++= Seq(
//   "-XX:MaxRAMFraction=1",
//   "-XX:+UnlockExperimentalVMOptions",
//   "-XX:+UseCGroupMemoryLimitForHeap")

// Scapegoat
scalaVersion in ThisBuild := scalaVersionNumber
scalaBinaryVersion in ThisBuild := scalaBinaryVersionNumber
scapegoatVersion in ThisBuild := "1.3.4"
compile.in(Compile) := (if (sys.env.get("NO_SCAPEGOAT").isEmpty)
  compile.in(Compile).dependsOn(scapegoat)
else compile.in(Compile)).value
