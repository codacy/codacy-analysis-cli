package com.codacy.analysis.cli

import com.codacy.analysis.core.analysis.Analyser
import com.codacy.plugins.api.languages.Language

sealed trait CLIErrorMessage {
  val message: String
}

object CLIErrorMessage {

  def from(coreError: Analyser.ErrorMessage): CLIErrorMessage = {
    coreError match {
      case Analyser.ErrorMessage.ToolExecutionFailure(toolType, toolName) =>
        CLIErrorMessage.ToolExecutionFailure(toolType, toolName)
      case Analyser.ErrorMessage.ToolNeedsNetwork(toolName) =>
        CLIErrorMessage.ToolNeedsNetwork(toolName)
      case Analyser.ErrorMessage.NonExistingToolInput(toolName, _) =>
        CLIErrorMessage.NonExistingToolInput(toolName)
      case Analyser.ErrorMessage.NoActiveToolInConfiguration =>
        CLIErrorMessage.NoActiveToolInConfiguration
      case Analyser.ErrorMessage.NoToolsFoundForFiles =>
        CLIErrorMessage.NoToolsFoundForFiles
    }
  }

  final case class CouldNotGetTools(errors: String) extends CLIErrorMessage {
    override val message: String = s"Could not get tools due to: $errors"
  }

  final case class NonExistingToolInput(toolName: String) extends CLIErrorMessage {
    override val message: String = s"""The selected tool "$toolName" is not supported or does not exist.
                                      |Use the --help option to get more information about available tools""".stripMargin
  }

  final case class NonExistentToolsFromRemoteConfiguration(tools: Set[String]) extends CLIErrorMessage {
    override val message: String =
      s"Could not find locally the following tools from remote configuration: ${tools.mkString(",")}"
  }

  final case class CodacyConfigurationFileError(error: String) extends CLIErrorMessage {
    override val message: String = s"Codacy configuration file error: $error"
  }

  case object FilesAccessError extends CLIErrorMessage {
    override val message: String = "Could not access project files"
  }

  final case class NoRemoteProjectConfiguration(error: String) extends CLIErrorMessage {
    override val message: String = s"Could not get remote project configuration: $error"
  }

  final case class NoToolsForLanguages(languages: Set[Language]) extends CLIErrorMessage {
    override val message: String = s"No tools for languages: ${languages.mkString(",")}"
  }

  final case class ToolExecutionFailure(toolType: String, toolName: String) extends CLIErrorMessage {
    override val message: String = s"Failed $toolType for $toolName"
  }

  final case class ToolNeedsNetwork(toolName: String) extends CLIErrorMessage {
    override val message: String =
      s"The tool $toolName needs network access to execute. Run with the parameter 'allow-network'."
  }
  case object NoActiveToolInConfiguration extends CLIErrorMessage {
    override val message: String = "No active tool found on the remote configuration"
  }
  case object NoToolsFoundForFiles extends CLIErrorMessage {
    override val message: String = "No tools found for files provided"
  }
  final case class UncommitedChanges(files: Set[String]) extends CLIErrorMessage {
    override val message: String = {
      s"""There are uncommited changes in the project.
         |Please commit them before selecting upload of your analysis:
         |Uncommited files:
         |${files.mkString("\n")}""".stripMargin
    }
  }

  final case class UploadError(mess: String) extends CLIErrorMessage {
    override val message: String = s"Error uploading results: $mess"
  }

  final case class MissingUploadRequisites(reason: String) extends CLIErrorMessage {
    override val message: String = s"Missing upload requisites: $reason"
  }

}
