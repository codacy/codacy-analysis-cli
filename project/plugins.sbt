addSbtPlugin("com.codacy" % "codacy-sbt-plugin" % "25.2.4")

// Coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.3.0")

// Static Analysis
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.2.13")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.34")

// Dependencies
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")

// Swagger code generation
addSbtPlugin("dev.guardrail" % "sbt-guardrail" % "1.0.0-M1")
libraryDependencies ++= Seq(
  "dev.guardrail" %% "guardrail-scala-support" % "1.0.0-M1",
  "dev.guardrail" %% "guardrail-scala-akka-http" % "1.0.0-M1")
dependencyOverrides ++= Seq(
  "org.scalameta" %% "scalameta" % "4.8.14",
  "org.scalameta" %% "trees" % "4.8.14",
  "org.scalameta" %% "parsers" % "4.8.14",
  "org.scalameta" %% "common" % "4.8.14",
  "org.scalameta" %% "tokenizers" % "4.8.14",
  "org.scalameta" %% "io" % "4.8.14")

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

evictionErrorLevel := Level.Warn
