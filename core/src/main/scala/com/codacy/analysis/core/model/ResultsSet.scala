package com.codacy.analysis.core.model

import java.nio.file.Path

import com.codacy.plugins.api.metrics.LineComplexity

final case class ToolResults(tool: String, issues: IssuesAnalysis)

sealed trait IssuesAnalysis

object IssuesAnalysis {
  final case class Success(results: Set[FileResults]) extends IssuesAnalysis
  final case class Failure(message: String) extends IssuesAnalysis

  final case class FileResults(filename: Path, results: Set[ToolResult])
}

final case class MetricsResult(language: String, metrics: Either[String, Set[FileWithMetrics]])
final case class FileWithMetrics(file: Path, metrics: Option[Metrics])
final case class Metrics(complexity: Option[Int],
                         loc: Option[Int],
                         cloc: Option[Int],
                         nrMethods: Option[Int],
                         nrClasses: Option[Int],
                         lineComplexities: Set[LineComplexity])

final case class DuplicationResult(language: String, duplication: Either[String, Set[DuplicationClone]])
