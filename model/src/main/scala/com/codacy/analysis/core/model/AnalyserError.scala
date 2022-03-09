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

  final case class NonExistingToolInput(toolName: String) extends AnalyserError {

    override val message: String =
      s"""The selected tool "$toolName" is not supported or does not exist""".stripMargin
  }

  final case class StandaloneToolInput(toolName: String) extends AnalyserError {

    override val message: String =
      s"""The selected tool "$toolName" is standalone and can't be run in the CLI.
      |Check https://docs.codacy.com/related-tools/local-analysis/client-side-tools for more info.""".stripMargin
  }

  case object NoActiveToolInConfiguration extends AnalyserError {
    override val message: String = "No active tool found on the remote configuration"
  }

  case object NoToolsFoundForFiles extends AnalyserError {
    override val message: String = "No tools found for files provided"
  }

  final case class FailedToFetchTools(errorMessage: String) extends AnalyserError {
    override val message: String = s"Failed to list tools with error: $errorMessage"
  }

  final case class FailedToListPatterns(toolUuid: String, errorMessage: String) extends AnalyserError {

    override val message: String =
      s"Failed to list patterns for tool with UUID: $toolUuid because of the following error: $errorMessage"
  }
}
