import sbt.Keys._
import sbt._

val scalaBinaryVersionNumber = "2.12"
val scalaVersionNumber = s"$scalaBinaryVersionNumber.10"

resolvers in ThisBuild += Resolver.bintrayIvyRepo("sbt", "sbt-plugin-releases")

lazy val aggregatedProjects: Seq[ProjectReference] = Seq(codacyAnalysisCore, codacyAnalysisCli)

lazy val testDependencies = Dependencies.specs2.map(_ % Test)

lazy val root = project
  .in(file("."))
  .settings(name := "root")
  .settings(
    inThisBuild(
      List(
        //Credentials for sonatype
        credentials += Credentials(
          "Sonatype Nexus Repository Manager",
          "oss.sonatype.org",
          sys.env.getOrElse("SONATYPE_USER", "username"),
          sys.env.getOrElse("SONATYPE_PASSWORD", "password")),
        scalaVersion := scalaVersionNumber,
        version := "0.1.0-SNAPSHOT",
        organization := "com.codacy",
        scalacOptions ++= Common.compilerFlags,
        scalacOptions.in(Test) ++= Seq("-Yrangepos"))))
  .settings(Common.genericSettings: _*)
  .aggregate(aggregatedProjects: _*)
  .settings(publish := {}, publishLocal := {}, publishArtifact := false)

lazy val codacyAnalysisCore = project
  .in(file("core"))
  .settings(name := "codacy-analysis-core")
  .settings(coverageExcludedPackages := "<empty>;com\\.codacy\\..*Error.*")
  .settings(Common.genericSettings: _*)
  .settings(
    // App Dependencies
    libraryDependencies ++= Seq(
      Dependencies.caseApp,
      Dependencies.betterFiles,
      Dependencies.jodaTime,
      Dependencies.fansi,
      Dependencies.scalajHttp,
      Dependencies.jGit,
      Dependencies.cats) ++
      Dependencies.circe ++
      Dependencies.log4s ++
      Dependencies.codacyPlugins,
    // Test Dependencies
    libraryDependencies ++= testDependencies)
  .settings(
    // Sonatype repository settings
    publishMavenStyle := true,
    publishArtifact.in(Test) := false,
    publish.in(Docker) := {},
    publishLocal.in(Docker) := {},
    pomIncludeRepository := { _ =>
      false
    },
    publishTo := sonatypePublishTo.value)
  .settings(
    organizationName := "Codacy",
    organizationHomepage := Some(new URL("https://www.codacy.com")),
    startYear := Some(2018),
    description := "Library to analyse projects",
    licenses := Seq("AGPL-3.0" -> url("https://opensource.org/licenses/AGPL-3.0")),
    homepage := Some(url("https://github.com/codacy/codacy-analysis-cli")),
    pomExtra := <scm>
      <url>https://github.com/codacy/codacy-analysis-cli</url>
      <connection>scm:git:git@github.com:codacy/codacy-analysis-cli.git</connection>
      <developerConnection>scm:git:https://github.com/codacy/codacy-analysis-cli.git</developerConnection>
    </scm>
      <developers>
        <developer>
          <id>rtfpessoa</id>
          <name>Rodrigo Fernandes</name>
          <email>rodrigo [at] codacy.com</email>
          <url>https://github.com/rtfpessoa</url>
        </developer>
        <developer>
          <id>bmbferreira</id>
          <name>Bruno Ferreira</name>
          <email>bruno.ferreira [at] codacy.com</email>
          <url>https://github.com/bmbferreira</url>
        </developer>
        <developer>
          <id>xplosunn</id>
          <name>Hugo Sousa</name>
          <email>hugo [at] codacy.com</email>
          <url>https://github.com/xplosunn</url>
        </developer>
        <developer>
          <id>pedrocodacy</id>
          <name>Pedro Amaral</name>
          <email>pamaral [at] codacy.com</email>
          <url>https://github.com/pedrocodacy</url>
        </developer>
      </developers>)

lazy val codacyAnalysisCli = project
  .in(file("cli"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(name := "codacy-analysis-cli")
  .settings(coverageExcludedPackages := "<empty>;com\\.codacy\\..*CLIError.*")
  .settings(Common.dockerSettings: _*)
  .settings(Common.genericSettings: _*)
  .settings(
    publish := publish.in(Docker).value,
    publishLocal := publishLocal.in(Docker).value,
    publishArtifact := false)
  .settings(
    // App Dependencies
    libraryDependencies ++= Seq(Dependencies.pprint),
    // Test Dependencies
    libraryDependencies ++= testDependencies)
  .dependsOn(codacyAnalysisCore % "compile->compile;test->test")
  .aggregate(codacyAnalysisCore)

// Scapegoat
scalaVersion in ThisBuild := scalaVersionNumber
scalaBinaryVersion in ThisBuild := scalaBinaryVersionNumber
scapegoatDisabledInspections in ThisBuild := Seq()
scapegoatVersion in ThisBuild := "1.4.1"
