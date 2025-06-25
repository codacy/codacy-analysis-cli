import sbt._

object Dependencies {

  val codacyPluginsVersion = "26.2.6_play_2.7"
  val circeVersion = "0.12.3"
  val specs2Version = "4.8.1"
  val codacyApiVersion = "26.20.0"
  val silencerVersion = "1.7.19"

  lazy val circe = List(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "io.circe" %% "circe-yaml" % "0.13.1")

  lazy val jodaTime = "joda-time" % "joda-time" % "2.10.6"

  lazy val log4s =
    Seq("ch.qos.logback" % "logback-classic" % "1.2.3", "org.log4s" %% "log4s" % "1.8.2")

  lazy val specs2 =
    Seq(
      "org.specs2" %% "specs2-core" % specs2Version,
      "org.specs2" %% "specs2-mock" % specs2Version,
      "org.specs2" %% "specs2-matcher-extra" % specs2Version).map(_ % Test)

  lazy val caseApp = "com.github.alexarchambault" %% "case-app" % "1.2.0"

  val codacyPlugins =
    Seq("codacy-plugins", "codacy-plugins-runner-binary").map("com.codacy" %% _ % codacyPluginsVersion)

  lazy val pluginsApi = "com.codacy" %% "codacy-plugins-api" % "8.1.1"

  lazy val pprint = "com.lihaoyi" %% "pprint" % "0.5.7"

  lazy val scalajHttp = "com.codacy" %% "scalaj-http" % "2.5.0"

  lazy val cats = "org.typelevel" %% "cats-core" % "2.1.0"

  lazy val jGit = "org.eclipse.jgit" % "org.eclipse.jgit" % "5.6.0.201912101111-r"

  lazy val typesafeConfig = "com.typesafe" % "config" % "1.4.0"

  val akkaVersion = "2.6.20"
  val akkaHttpVersion = "10.2.10"

  val akka =
    Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion)

  val macroParadise = "org.scalamacros" %% "paradise" % "2.1.1"

  val silencer = Seq(
    compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full)

  val scalatest = "org.scalatest" %% "scalatest" % "3.0.8"
  val betterFiles = "com.github.pathikrit" %% "better-files" % "3.8.0"
}
