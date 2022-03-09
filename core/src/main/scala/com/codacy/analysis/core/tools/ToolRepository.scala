package com.codacy.analysis.core.tools

import com.codacy.analysis.core.model.{AnalyserError, DuplicationToolSpec, MetricsToolSpec, PatternSpec, ToolSpec}

trait ToolRepository {

  protected def allTools: Either[AnalyserError, Seq[ToolSpec]]

  def listSupportedTools(): Either[AnalyserError, Seq[ToolSpec]] =
    allTools.map(_.filterNot(_.standalone))

  def getTool(queryString: String): Either[AnalyserError, ToolSpec] =
    allTools.flatMap { toolsSpecs =>
      toolsSpecs.find(
        tool => tool.shortName.equalsIgnoreCase(queryString) || tool.uuid.equalsIgnoreCase(queryString)) match {
        case None                          => Left(AnalyserError.NonExistingToolInput(queryString))
        case Some(tool) if tool.standalone => Left(AnalyserError.StandaloneToolInput(queryString))
        case Some(tool)                    => Right(tool)
      }
    }
  def listDuplicationTools(): Either[AnalyserError, Seq[DuplicationToolSpec]]
  def listMetricsTools(): Either[AnalyserError, Seq[MetricsToolSpec]]
  def listPatterns(tool: ToolSpec): Either[AnalyserError, Seq[PatternSpec]]

}
