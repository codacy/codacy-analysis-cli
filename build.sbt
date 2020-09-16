import sbt._
import codacy.libs._

Universal / javaOptions ++= Seq("-Xms1g", "-Xmx2g", "-Xss512m", "-XX:+UseG1GC", "-XX:+UseStringDeduplication")

val sonatypeInformation = Seq(
  startYear := Some(2018),
  homepage := Some(url("https://github.com/codacy/codacy-analysis-cli")),
  // HACK: This setting is not picked up properly from the plugin
  pgpPassphrase := Option(System.getenv("SONATYPE_GPG_PASSPHRASE")).map(_.toCharArray),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/codacy/codacy-analysis-cli"),
      "scm:git:git@github.com:codacy/codacy-analysis-cli.git"))) ++ publicMvnPublish

lazy val codacyAnalysisCore = project
  .in(file("core"))
  .settings(name := "codacy-analysis-core")
  .settings(coverageExcludedPackages := "<empty>;com\\.codacy\\..*Error.*")
  .settings(Common.genericSettings)
  .settings(
    // App Dependencies
    libraryDependencies ++= Seq(
      Dependencies.caseApp,
      betterFiles,
      Dependencies.jodaTime,
      Dependencies.scalajHttp,
      Dependencies.jGit,
      Dependencies.cats,
      Dependencies.typesafeConfig) ++
      Dependencies.circe ++
      Dependencies.log4s ++
      Dependencies.codacyPlugins,
    // Test Dependencies
    libraryDependencies ++= Dependencies.specs2,
    sonatypeInformation,
    description := "Library to analyse projects")
  // Disable legacy Scalafmt plugin imported by codacy-sbt-plugin
  .disablePlugins(com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin)
  .dependsOn(codacyAnalysisModels)

lazy val codacyAnalysisCli = project
  .in(file("cli"))
  .settings(
    name := "codacy-analysis-cli",
    coverageExcludedPackages := "<empty>;com\\.codacy\\..*CLIError.*",
    Common.dockerSettings,
    Common.genericSettings,
    Common.nativeImageSettings,
    Universal / javaOptions ++= Seq("-XX:MinRAMPercentage=60.0", "-XX:MaxRAMPercentage=90.0"),
    publish := (Docker / publish).value,
    publishLocal := (Docker / publishLocal).value,
    publishArtifact := false,
    libraryDependencies ++= Dependencies.pprint +: Dependencies.specs2)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(NativeImagePlugin)
  // Disable legacy Scalafmt plugin imported by codacy-sbt-plugin
  .disablePlugins(com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin)
  .dependsOn(codacyAnalysisCore % "compile->compile;test->test")
  .aggregate(codacyAnalysisCore)

lazy val codacyAnalysisModels = project
  .in(file("model"))
  .settings(
    crossScalaVersions := Common.supportedScalaVersions,
    name := "codacy-analysis-cli-model",
    Common.genericSettings,
    publishTo := sonatypePublishToBundle.value,
    libraryDependencies ++=
      Dependencies.circe ++ Seq(Dependencies.pluginsApi) ++ Dependencies.specs2,
    description := "Library with analysis models")
  .settings(sonatypeInformation)
  // Disable legacy Scalafmt plugin imported by codacy-sbt-plugin
  .disablePlugins(com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin)
  .enablePlugins(JavaAppPackaging)

// Scapegoat
ThisBuild / scalaVersion := Common.scalaVersionNumber
ThisBuild / scalaBinaryVersion := Common.scalaBinaryVersionNumber
ThisBuild / scapegoatDisabledInspections := Seq()
ThisBuild / scapegoatVersion := "1.4.3"
