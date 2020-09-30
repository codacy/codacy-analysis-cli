package com.codacy.toolRepository.plugins

import com.codacy.analysis.core.model.{AnalyserError, PatternSpec, ToolSpec}
import com.codacy.analysis.core.tools.ToolRepository

//TODO: Implement me
class ToolRepositoryPlugins() extends ToolRepository {

  override def list(): Either[AnalyserError, Seq[ToolSpec]] = ???

  override def get(uuid: String): Either[AnalyserError, ToolSpec] = ???

  override def listPatterns(toolUuid: String): Either[AnalyserError, Seq[PatternSpec]] = ???
}
