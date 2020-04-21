//Packaging (Sonatype)
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.2")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.1")

// Packaging (Docker)
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.0")

// Coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")

// Static Analysis
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.1.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.15")

// Formatting
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.4")

// Dependencies
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.5.0")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")
