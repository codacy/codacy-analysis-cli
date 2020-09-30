package com.codacy.analysis.core.tools

import com.codacy.analysis.core.model.{PatternSpec, ToolSpec}

trait ToolRepository {

  def list(): Either[String, Seq[ToolSpec]]
  def get(uuid: String): Either[String, ToolSpec]
  def listPatterns(toolUuid: String): Either[String, Seq[PatternSpec]]

}
