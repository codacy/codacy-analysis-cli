addSbtPlugin("com.codacy" % "codacy-sbt-plugin" % "25.2.4")

// Coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.3.0")

// Static Analysis
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.2.13")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.34")

// Dependencies
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")

// Swagger code generation
addSbtPlugin("com.twilio" % "sbt-guardrail" % "0.59.0")

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

evictionErrorLevel := Level.Warn
