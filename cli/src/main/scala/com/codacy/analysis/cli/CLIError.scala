package com.codacy.analysis.cli

import com.codacy.analysis.core.git.Commit
import com.codacy.analysis.core.model.AnalyserError
import com.codacy.plugins.api.languages.Language

sealed trait CLIError {
  val message: String
}

object CLIError {

  def from(coreError: AnalyserError): CLIError = {
    coreError match {
      case AnalyserError.ToolExecutionFailure(toolType, toolName) => CLIError.ToolExecutionFailure(toolType, toolName)
      case AnalyserError.ToolNeedsNetwork(toolName)               => CLIError.ToolNeedsNetwork(toolName)
      case AnalyserError.NonExistingToolInput(toolName)           => CLIError.NonExistingToolInput(toolName)
      case AnalyserError.StandaloneToolInput(toolName)            => CLIError.StandaloneToolInput(toolName)
      case AnalyserError.NoActiveToolInConfiguration              => CLIError.NoActiveToolInConfiguration
      case AnalyserError.NoToolsFoundForFiles                     => CLIError.NoToolsFoundForFiles
      case AnalyserError.FailedToFetchTools(errorMessage)         => CLIError.CouldNotGetTools(errorMessage)
      case AnalyserError.FailedToListPatterns(toolUuid, errorMessage) =>
        CLIError.CouldNotGetTools(s"Failure getting patterns for tool with UUID $toolUuid. Error: $errorMessage")
    }
  }

  final case class CouldNotGetTools(errors: String) extends CLIError {
    override val message: String = s"Could not get tools due to: $errors"
  }

  final case class NonExistingToolInput(toolName: String) extends CLIError {

    override val message: String =
      s"""The selected tool "$toolName" is not supported or does not exist.
      |Use the --help option to get more information about available tools""".stripMargin
  }

  final case class StandaloneToolInput(toolName: String) extends CLIError {

    override val message: String =
      s"""The selected tool "$toolName" is standalone and can't be run in the CLI.
      |Check https://docs.codacy.com/related-tools/local-analysis/client-side-tools for more info.""".stripMargin
  }

  final case class NonExistentToolsFromRemoteConfiguration(tools: Set[String]) extends CLIError {

    override val message: String =
      s"Could not find locally the following tools from remote configuration: ${tools.mkString(",")}"
  }

  final case class CodacyConfigurationFileError(error: String) extends CLIError {
    override val message: String = s"Codacy configuration file error: $error"
  }

  case object FilesAccessError extends CLIError {
    override val message: String = "Could not access project files"
  }

  final case class NoRemoteProjectConfiguration(error: String) extends CLIError {
    override val message: String = s"Could not get remote project configuration: $error"
  }

  final case class NoToolsForLanguages(languages: Set[Language]) extends CLIError {
    override val message: String = s"No tools for languages: ${languages.mkString(",")}"
  }

  final case class ToolExecutionFailure(toolType: String, toolName: String) extends CLIError {
    override val message: String = s"Failed $toolType for $toolName"
  }

  final case class ToolNeedsNetwork(toolName: String) extends CLIError {

    override val message: String =
      s"The tool $toolName needs network access to execute. Run with the parameter --allow-network."
  }

  case object NoActiveToolInConfiguration extends CLIError {
    override val message: String = "No active tool found on the remote configuration"
  }

  case object NoToolsFoundForFiles extends CLIError {
    override val message: String = "No tools found for files provided"
  }

  final case class UncommitedChanges(files: Set[String]) extends CLIError {

    override val message: String = {
      s"""There are uncommitted changes in the project.
         |Please commit them before running your analysis:
         |Uncommited files:
         |${files.mkString("\n")}""".stripMargin
    }
  }

  final case class UploadError(reason: String) extends CLIError {
    override val message: String = s"Error uploading results: $reason"
  }

  final case class MissingUploadRequisites(reason: String) extends CLIError {
    override val message: String = s"Missing upload requisites: $reason"
  }

  final case class CommitUuidsDoNotMatch(paramCommit: Commit.Uuid, gitCommit: Commit.Uuid) extends CLIError {
    override val message: String = s"""The commit uuid provided by parameter (${paramCommit.value})
      | does not match the one from your current git branch (${gitCommit.value}""".stripMargin
  }
}
