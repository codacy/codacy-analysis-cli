package com.codacy.analysis.core.model

import java.nio.file.Path

sealed trait ResultsSet

final case class ToolResults(tool: String, fileResults: Set[FileResults]) extends ResultsSet

final case class FileResults(filename: Path, results: Set[ToolResult], metrics: Option[FileMetrics])
