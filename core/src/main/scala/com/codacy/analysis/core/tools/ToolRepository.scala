package com.codacy.analysis.core.tools

import com.codacy.analysis.core.model.{AnalyserError, PatternSpec, ToolSpec}

trait ToolRepository {

  def list(): Either[AnalyserError, Seq[ToolSpec]]
  def get(uuid: String): Either[AnalyserError, ToolSpec]
  def listPatterns(toolUuid: String): Either[AnalyserError, Seq[PatternSpec]]

}
