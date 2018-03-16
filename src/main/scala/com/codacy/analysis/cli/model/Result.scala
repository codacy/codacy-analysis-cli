package com.codacy.analysis.cli.model

sealed trait Location

final case class FullLocation(line: Int, position: Int) extends Location

final case class LineLocation(line: Int) extends Location

sealed trait Result

final case class Issue(location: Location, filename: String) extends Result

final case class FileError(filename: String, message: String) extends Result
