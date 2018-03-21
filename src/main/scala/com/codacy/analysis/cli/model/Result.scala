package com.codacy.analysis.cli.model

import java.nio.file.Path

import codacy.docker.api

sealed trait Location

final case class FullLocation(line: Int, column: Int) extends Location

final case class LineLocation(line: Int) extends Location

sealed trait Result

final case class Issue(patternId: api.Pattern.Id,
                       filename: Path,
                       message: Issue.Message,
                       level: api.Result.Level,
                       category: Option[api.Pattern.Category],
                       location: Location)
    extends Result

object Issue {
  final case class Message(text: String)
}

final case class FileError(filename: Path, message: String) extends Result
