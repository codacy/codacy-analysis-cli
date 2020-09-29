package com.codacy.analysis.core.tools

import com.codacy.analysis.core.model.{PatternSpec, ToolSpec}

trait ToolRepository {

  def list(): Seq[ToolSpec]
  def listPatterns(toolUuid: String): Seq[PatternSpec]

}
