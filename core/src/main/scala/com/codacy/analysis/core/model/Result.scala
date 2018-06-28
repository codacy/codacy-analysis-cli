package com.codacy.analysis.core.model

import java.nio.file.Path

import com.codacy.plugins.api.results
import com.codacy.plugins.duplication.api.DuplicationCloneFile

sealed trait Location

final case class FullLocation(line: Int, column: Int) extends Location {
  override def toString: String = s"$line:$column"
}

final case class LineLocation(line: Int) extends Location {
  override def toString: String = line.toString
}

sealed trait Result

final case class Issue(patternId: results.Pattern.Id,
                       filename: Path,
                       message: Issue.Message,
                       level: results.Result.Level,
                       category: Option[results.Pattern.Category],
                       location: Location)
    extends Result

case class DuplicationClone(nrTokens: Int, nrLines: Int, files: Seq[DuplicationCloneFile])
    extends Result

object Issue {

  final case class Message(text: String) extends AnyVal {
    override def toString: String = text
  }

}

final case class FileError(filename: Path, message: String) extends Result

final case class FileResults(filename: Path, results: Set[Result])

sealed trait ResultsSet

final case class ToolResults(tool: String, fileResults: Set[FileResults]) extends ResultsSet
