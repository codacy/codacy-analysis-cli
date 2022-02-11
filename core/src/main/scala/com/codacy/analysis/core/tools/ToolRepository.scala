package com.codacy.analysis.core.tools

import com.codacy.analysis.core.model.{AnalyserError, DuplicationToolSpec, MetricsToolSpec, PatternSpec, ToolSpec}

trait ToolRepository {

  def listTools(): Either[AnalyserError, Seq[ToolSpec]]
  def listDuplicationTools(): Either[AnalyserError, Seq[DuplicationToolSpec]]
  def listMetricsTools(): Either[AnalyserError, Seq[MetricsToolSpec]]
  def getTool(uuid: String): Either[AnalyserError, ToolSpec]
  def listPatterns(tool: ToolSpec): Either[AnalyserError, Seq[PatternSpec]]

}
