package com.codacy.analysis.core.model

sealed trait AnalyserError {
  val message: String
}

object AnalyserError {

  final case class ToolExecutionFailure(toolType: String, toolName: String) extends AnalyserError {
    override val message: String = s"Failed $toolType for $toolName"
  }

  final case class ToolNeedsNetwork(toolName: String) extends AnalyserError {
    override val message: String = s"The tool $toolName needs network access to execute."
  }

  //TODO: Consider if we want to list the tools, and how we want to show them
  final case class NonExistingToolInput(toolName: String) extends AnalyserError {

    override val message: String =
      s"""The selected tool "$toolName" is not supported or does not exist""".stripMargin
  }

  case object NoActiveToolInConfiguration extends AnalyserError {
    override val message: String = "No active tool found on the remote configuration"
  }

  case object NoToolsFoundForFiles extends AnalyserError {
    override val message: String = "No tools found for files provided"
  }

}
