package com.codacy.toolRepository.plugins

import com.codacy.analysis.core.model.{PatternSpec, ToolSpec}
import com.codacy.analysis.core.tools.ToolRepository

//TODO: Implement me
class ToolRepositoryPlugins() extends ToolRepository {

  override def list(): Either[String, Seq[ToolSpec]] = ???

  override def listPatterns(toolUuid: String): Either[String, Seq[PatternSpec]] = ???
}
