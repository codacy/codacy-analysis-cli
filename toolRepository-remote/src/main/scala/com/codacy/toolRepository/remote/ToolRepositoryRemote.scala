package com.codacy.toolRepository.remote

import com.codacy.analysis.core.model.{PatternSpec, ToolSpec}
import com.codacy.analysis.core.tools.ToolRepository

//TODO: Implement me
class ToolRepositoryRemote() extends ToolRepository {

  override def list(): Either[String, Seq[ToolSpec]] = ???

  override def listPatterns(toolUuid: String): Either[String, Seq[PatternSpec]] = ???
}
