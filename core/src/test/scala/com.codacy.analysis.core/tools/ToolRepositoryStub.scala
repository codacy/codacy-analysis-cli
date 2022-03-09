package com.codacy.analysis.core.tools

import com.codacy.analysis.core.model
import com.codacy.analysis.core.model.{AnalyserError, PatternSpec, ToolSpec}

class ToolRepositoryStub extends ToolRepository {

  def allTools: Either[AnalyserError, Seq[ToolSpec]] = ???
  def listDuplicationTools(): Either[AnalyserError, Seq[model.DuplicationToolSpec]] = ???
  def listMetricsTools(): Either[AnalyserError, Seq[model.MetricsToolSpec]] = ???
  def listPatterns(tool: ToolSpec): Either[AnalyserError, Seq[PatternSpec]] = ???

}
