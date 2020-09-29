addSbtPlugin("com.codacy" % "codacy-sbt-plugin" % "20.0.7")

// Coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")

// Static Analysis
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.1.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.11")

// Formatting
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.2")

// Dependencies
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.5.1")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.15.0")

addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.2.1")
