package com.codacy.analysis.core.model

import java.nio.file.Path

final case class ToolResults(tool: String, fileResults: Set[FileResults])
final case class FileResults(filename: Path, results: Set[ToolResult])

final case class MetricsResult(results: Set[FileMetrics], analysisError: Option[String])
