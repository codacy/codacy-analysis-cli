addSbtPlugin("com.codacy" % "codacy-sbt-plugin" % "25.0.0")

// Coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.6")

// Static Analysis
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.1.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.21")

// Dependencies
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.15.0")

// Swagger code generation
addSbtPlugin("com.twilio" % "sbt-guardrail" % "0.59.0")

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
