package com.codacy.analysis.core.tools

import com.codacy.analysis.core.model
import com.codacy.analysis.core.model.{AnalyserError, PatternSpec, ToolSpec}

class ToolRepositoryStub extends ToolRepository {

  def listTools(): Either[AnalyserError, Seq[ToolSpec]] = ???
  def listDuplicationTools(): Either[AnalyserError, Seq[model.DuplicationToolSpec]] = ???
  def getTool(uuid: String): Either[AnalyserError, ToolSpec] = ???
  def listPatterns(tool: ToolSpec): Either[AnalyserError, Seq[PatternSpec]] = ???

}
