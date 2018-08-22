resolvers ++= Seq("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/releases")

//Packaging (Sonatype)
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2-1")

// Packaging (Docker)
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.6")

// Coverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")
addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "1.3.15")

// Static Analysis
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.9")
// addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.6.0-M15")
// addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.5.10")

// Formating
// addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.6.0-RC4")
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.5.1")

// Dependencies
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.4")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.1")
addSbtPlugin("net.vonbuchholtz" % "sbt-dependency-check" % "0.2.8")
