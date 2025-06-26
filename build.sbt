import java.nio.file.Files

import sbt._

val assemblyCommon = Seq(
  assembly / test := {},
  // Without this assembly merge strategy, gives the following error:
  // (codacyAnalysisCli / assembly) deduplicate: different file contents found in the following:
  // [error] org/bouncycastle/bcpg-jdk15on/1.64/bcpg-jdk15on-1.64.jar:META-INF/versions/9/module-info.class
  // Workaround:
  // https://stackoverflow.com/questions/54834125/sbt-assembly-deduplicate-module-info-class
  assembly / assemblyMergeStrategy := {
    case "META-INF/versions/9/module-info.class" => MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  })

inThisBuild(Seq(scalaVersion := Common.scalaVersionNumber, scalaBinaryVersion := Common.scalaBinaryVersionNumber))

val sonatypeInformation = Seq(
  startYear := Some(2018),
  homepage := Some(url("https://github.com/codacy/codacy-analysis-cli")),
  // HACK: This setting is not picked up properly from the plugin
  pgpPassphrase := Option(System.getenv("SONATYPE_GPG_PASSPHRASE")).map(_.toCharArray),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/codacy/codacy-analysis-cli"),
      "scm:git:git@github.com:codacy/codacy-analysis-cli.git")))

lazy val codacyAnalysisCore = project
  .in(file("core"))
  .settings(name := "codacy-analysis-core")
  .settings(coverageExcludedPackages := "<empty>;com\\.codacy\\..*Error.*")
  .settings(Common.genericSettings)
  .settings(
    // App Dependencies
    libraryDependencies ++= Seq(
      Dependencies.caseApp,
      Dependencies.betterFiles,
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
    description := "Library to analyze projects")
  .settings(assemblyCommon: _*)
  .dependsOn(codacyAnalysisModels)

lazy val codacyAnalysisCli = project
  .in(file("cli"))
  .settings(
    name := "codacy-analysis-cli",
    coverageExcludedPackages := "<empty>;com\\.codacy\\..*CLIError.*",
    Common.genericSettings,
    Universal / javaOptions ++= Seq("-XX:MinRAMPercentage=60.0", "-XX:MaxRAMPercentage=90.0"),
    publish := (Docker / publish).value,
    publishLocal := (Docker / publishLocal).value,
    publishArtifact := false,
    assembly / assemblyJarName := "codacy-analysis-cli-assembly.jar",
    libraryDependencies ++= Dependencies.pprint +: Dependencies.specs2)
  .settings(assemblyCommon: _*)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .dependsOn(codacyAnalysisCore % "compile->compile;test->test", toolRepositoryRemote)
  .aggregate(codacyAnalysisCore, toolRepositoryRemote)

lazy val toolRepositoryRemote = project
  .in(file("toolRepository-remote"))
  .settings(Common.genericSettings, libraryDependencies ++= Dependencies.specs2)
  .settings(assemblyCommon: _*)
  .dependsOn(codacyAnalysisCore % "compile->compile;test->test", codacyAnalysisModels, codacyApiClient)

lazy val codacyAnalysisModels = project
  .in(file("model"))
  .settings(
    crossScalaVersions := Common.supportedScalaVersions,
    name := "codacy-analysis-cli-model",
    Common.genericSettings,
    libraryDependencies ++=
      Dependencies.circe ++ Seq(Dependencies.pluginsApi) ++ Dependencies.specs2,
    description := "Library with analysis models")
  .settings(assemblyCommon: _*)
  .settings(sonatypeInformation)
  .enablePlugins(JavaAppPackaging)

lazy val apiSwaggerFile: File =
  codacyApiClient.base / "target" / "api" / "codacy-api" / Dependencies.codacyApiVersion / "swagger.yaml"

lazy val downloadCodacyToolsSwaggerFile = Def.task[Unit] {
  if (!Files.exists(apiSwaggerFile.toPath)) {
    val result: String =
      scala.io.Source
        .fromURL(url(s"https://artifacts.codacy.com/api/codacy-api/${Dependencies.codacyApiVersion}/apiv3.yaml"))
        .mkString
    IO.write(apiSwaggerFile, result)
  }
}

val silencerSettings =
  Seq(libraryDependencies ++= Dependencies.silencer, scalacOptions += "-P:silencer:pathFilters=src_managed")

lazy val codacyApiClient = project
  .in(file("codacy-api-client"))
  .settings(name := "codacy-api-client", description := "Client library for codacy API")
  .settings(
    // Guardrail requirement
    addCompilerPlugin(Dependencies.macroParadise.cross(CrossVersion.full)),
    scalacOptions += "-Xexperimental")
  .settings(
    libraryDependencies ++= Dependencies.akka ++ Dependencies.circe ++ Seq(
      Dependencies.typesafeConfig,
      Dependencies.cats,
      Dependencies.scalatest % Test))
  .settings(
    Compile / guardrail := (Compile / guardrail).dependsOn(downloadCodacyToolsSwaggerFile).value,
    Compile / guardrailTasks := {
      List(
        ScalaClient(
          specPath = apiSwaggerFile,
          pkg = "com.codacy.analysis.clientapi",
          tracing = false,
          modules = List("circe", "akka-http")))
    },
    silencerSettings)
