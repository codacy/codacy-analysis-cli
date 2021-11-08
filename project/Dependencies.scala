import sbt._

object Dependencies {

  val codacyPluginsVersion = "24.4.5_play_2.7"
  val circeVersion = "0.12.3"
  val specs2Version = "4.8.1"
  val codacyApiVersion = "20.31.0"
  val silencerVersion = "1.7.0"

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

  lazy val pluginsApi = "com.codacy" %% "codacy-plugins-api" % "5.3.1"

  lazy val pprint = "com.lihaoyi" %% "pprint" % "0.5.7"

  lazy val scalajHttp = "org.scalaj" %% "scalaj-http" % "2.4.2"

  lazy val cats = "org.typelevel" %% "cats-core" % "2.1.0"

  lazy val jGit = "org.eclipse.jgit" % "org.eclipse.jgit" % "5.6.0.201912101111-r"

  lazy val typesafeConfig = "com.typesafe" % "config" % "1.4.0"

  val akkaVersion = "2.5.26"
  val akkaHttpVersion = "10.1.9"

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
}
