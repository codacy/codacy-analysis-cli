import sbt._

object Dependencies {

  val circeVersion = "0.9.3"
  lazy val circe = List(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion)

  val jacksonVersion = "2.8.4"
  lazy val jackson = Seq(
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % jacksonVersion)

  lazy val betterFiles = "com.github.pathikrit" %% "better-files" % "3.4.0"

  lazy val jodaTime = "joda-time" % "joda-time" % "2.9.9"

  lazy val log4s = Seq("ch.qos.logback" % "logback-classic" % "1.2.3", "org.log4s" %% "log4s" % "1.5.0")

  val specs2Version = "4.0.2"
  lazy val specs2 = Seq("org.specs2" %% "specs2-core" % specs2Version, "org.specs2" %% "specs2-mock" % specs2Version)

  lazy val caseApp = "com.github.alexarchambault" %% "case-app" % "1.2.0"

  lazy val codacyPlugins =
    Seq("com.codacy" %% "codacy-plugins-api" % "3.0.96", "codacy" %% "codacy-plugins" % "5.0.117")

  lazy val fansi = "com.lihaoyi" %% "fansi" % "0.2.5"

  lazy val scalajHttp = "org.scalaj" %% "scalaj-http" % "2.3.0"

  lazy val cats = "org.typelevel" %% "cats-core" % "1.0.1"

  lazy val jGit = "org.eclipse.jgit" % "org.eclipse.jgit" % "5.0.0.201806131550-r"
}
