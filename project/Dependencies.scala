import sbt._

object Dependencies {

  val codacyPluginsVersion = "15.1.8_play_2.7"
  val circeVersion = "0.12.3"
  val specs2Version = "4.8.1"

  lazy val circe = List(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "io.circe" %% "circe-yaml" % "0.12.0")

  lazy val betterFiles = "com.github.pathikrit" %% "better-files" % "3.6.0"

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
    Seq("com.codacy" %% "codacy-plugins", "com.codacy" %% "codacy-plugins-runner-binary").map(_ % codacyPluginsVersion)

  lazy val pluginsApi = "com.codacy" %% "codacy-plugins-api" % "4.0.2"

  lazy val pprint = "com.lihaoyi" %% "pprint" % "0.5.7"

  lazy val scalajHttp = "org.scalaj" %% "scalaj-http" % "2.4.2"

  lazy val cats = "org.typelevel" %% "cats-core" % "2.1.0"

  lazy val jGit = "org.eclipse.jgit" % "org.eclipse.jgit" % "5.6.0.201912101111-r"

  lazy val typesafeConfig = "com.typesafe" % "config" % "1.4.0"
}
