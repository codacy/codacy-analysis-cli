package com.codacy.analysis.core.analysis

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.core.model.{Configuration, DuplicationClone, FileMetrics, ToolResult}
import com.codacy.analysis.core.tools.{DuplicationTool, MetricsTool, Tool}
import org.log4s.{Logger, getLogger}

import scala.concurrent.duration.Duration
import scala.util.Try

trait AnalyserCompanion[T[_]] {
  def name: String
  def apply(): Analyser[T]
}

trait Analyser[T[_]] {

  def analyse(tool: Tool,
              directory: File,
              files: Set[Path],
              config: Configuration,
              timeout: Option[Duration] = Option.empty[Duration]): T[Set[ToolResult]]

  def metrics(metricsTool: MetricsTool,
              directory: File,
              files: Option[Set[Path]],
              timeout: Option[Duration] = Option.empty[Duration]): T[Set[FileMetrics]]

  def duplication(duplicationTool: DuplicationTool,
                  directory: File,
                  files: Set[Path],
                  timeout: Option[Duration] = Option.empty[Duration]): T[Set[DuplicationClone]]

}

object Analyser {

  private val logger: Logger = getLogger

  val defaultAnalyser: AnalyserCompanion[Try] = CodacyPluginsAnalyser

  val allAnalysers: Set[AnalyserCompanion[Try]] = Set(defaultAnalyser)

  def apply(name: String): Analyser[Try] = {
    val builder = allAnalysers.find(_.name.equalsIgnoreCase(name)).getOrElse {
      logger.warn(s"Could not find analyser for name $name. Using ${defaultAnalyser.name} as fallback.")
      defaultAnalyser
    }

    builder()
  }

  sealed trait Error {
    val message: String
  }

  object Error {
    final case class ToolExecutionFailure(toolType: String, toolName: String) extends Error {
      override val message: String = s"Failed $toolType for $toolName"
    }
    final case class ToolNeedsNetwork(toolName: String) extends Error {
      override val message: String = s"The tool $toolName needs network access to execute."
    }
    final case class NonExistingToolInput(toolName: String, availableTools: Set[String]) extends Error {
      override val message: String = s"""The selected tool "$toolName" is not supported or does not exist.
                                        |The tool should be one of (${availableTools.mkString(", ")})""".stripMargin
    }
    case object NoActiveToolInConfiguration extends Error {
      override val message: String = "No active tool found on the remote configuration"
    }

    case object NoToolsFoundForFiles extends Error {
      override val message: String = "No tools found for files provided"
    }

  }
}
