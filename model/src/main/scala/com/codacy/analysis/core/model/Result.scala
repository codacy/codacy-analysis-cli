package com.codacy.analysis.core.model

import java.nio.file.Path

import com.codacy.plugins.api.metrics.LineComplexity
import com.codacy.plugins.api.results
import com.codacy.plugins.api.duplication.DuplicationCloneFile

sealed trait Result

final case class DuplicationClone(cloneLines: String, nrTokens: Int, nrLines: Int, files: Set[DuplicationCloneFile])
    extends Result

sealed trait ToolResult extends Result

final case class FileError(filename: Path, message: String) extends ToolResult

final case class Issue(patternId: results.Pattern.Id,
                       filename: Path,
                       message: Issue.Message,
                       level: results.Result.Level,
                       category: Option[results.Pattern.Category],
                       location: Location)
    extends ToolResult

object Issue {

  final case class Message(text: String) extends AnyVal {
    override def toString: String = text
  }
}

final case class FileMetrics(filename: Path,
                             complexity: Option[Int],
                             loc: Option[Int],
                             cloc: Option[Int],
                             nrMethods: Option[Int],
                             nrClasses: Option[Int],
                             lineComplexities: Set[LineComplexity])
    extends Result
