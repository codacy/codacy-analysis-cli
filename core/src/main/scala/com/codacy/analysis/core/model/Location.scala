package com.codacy.analysis.core.model

sealed trait Location {
  val line: Int
}

final case class FullLocation(val line: Int, column: Int) extends Location {
  override def toString: String = s"$line:$column"
}

final case class LineLocation(val line: Int) extends Location {
  override def toString: String = line.toString
}
