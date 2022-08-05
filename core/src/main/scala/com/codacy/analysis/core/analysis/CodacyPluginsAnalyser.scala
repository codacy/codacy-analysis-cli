package com.codacy.analysis.core.analysis

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.tools.{DuplicationTool, MetricsTool, Tool}
import com.codacy.plugins.api.Source
import org.log4s.{Logger, getLogger}

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

final class CodacyPluginsAnalyser() extends Analyser {

  private val logger: Logger = getLogger

  override def analyse(tool: Tool,
                       directory: File,
                       files: Set[Path],
                       config: Configuration,
                       tmpDirectory: Option[File],
                       timeout: Option[Duration] = Option.empty[Duration],
                       maxToolMemory: Option[String] = None): Try[Set[ToolResult]] = {
    val result = tool.run(directory, files, config, tmpDirectory, timeout, maxToolMemory)

    result match {
      case Success(res) =>
        logger.info(s"Completed analysis for ${tool.name} with ${res.size} results")
      case Failure(e) =>
        logger.error(e)(AnalyserError.ToolExecutionFailure("analysis", tool.name).message)
    }

    result
  }

  override def metrics(metricsTool: MetricsTool,
                       directory: File,
                       files: Set[Path],
                       tmpDirectory: Option[File],
                       timeout: Option[Duration] = Option.empty[Duration],
                       maxToolMemory: Option[String] = None): Try[Set[FileMetrics]] = {

    val srcFiles = files.map(filePath => Source.File(filePath.toString))

    val result = metricsTool.run(directory, srcFiles, tmpDirectory, timeout, maxToolMemory)

    result match {
      case Success(res) =>
        logger.info(s"Completed metrics for ${metricsTool.name} with ${res.size} results")
      case Failure(e) =>
        logger.error(e)(AnalyserError.ToolExecutionFailure("metrics", metricsTool.name).message)
    }

    result.map(_.to[Set])
  }

  override def duplication(duplicationTool: DuplicationTool,
                           directory: File,
                           files: Set[Path],
                           tmpDirectory: Option[File],
                           timeout: Option[Duration] = Option.empty[Duration],
                           maxToolMemory: Option[String] = None): Try[Set[DuplicationClone]] = {

    val result = duplicationTool.run(directory, files, tmpDirectory, timeout, maxToolMemory)

    result match {
      case Success(res) =>
        logger.info(s"Completed duplication for ${duplicationTool.name} with ${res.size} results")
      case Failure(e) =>
        logger.error(e)(AnalyserError.ToolExecutionFailure("duplication", duplicationTool.name).message)
    }

    result.map(_.to[Set])
  }

}
