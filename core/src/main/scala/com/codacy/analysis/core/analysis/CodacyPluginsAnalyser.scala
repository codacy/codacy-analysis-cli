package com.codacy.analysis.core.analysis

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.tools.{MetricsTool, Tool}
import com.codacy.plugins.api.Source
import org.log4s.{Logger, getLogger}

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class CodacyPluginsAnalyser extends Analyser[Try] {

  private val logger: Logger = getLogger

  override def analyse(tool: Tool,
                       directory: File,
                       files: Set[Path],
                       config: Configuration,
                       timeout: Option[Duration] = Option.empty[Duration]): Try[Set[ToolResult]] = {
    val result = tool.run(directory, files, config, timeout)

    result match {
      case Success(res) =>
        logger.info(s"Completed analysis for ${tool.name} with ${res.size} results")
      case Failure(e) =>
        logger.error(e)(s"Failed analysis for ${tool.name}")
    }

    result
  }

  override def metrics(metricsTool: MetricsTool,
                       directory: File,
                       files: Option[Set[Path]],
                       timeout: Option[Duration] = Option.empty[Duration]): Try[Set[FileMetrics]] = {

    val srcFiles = files.map(_.map(filePath => Source.File(filePath.toString)))

    val result = metricsTool.run(directory, srcFiles, timeout)

    result match {
      case Success(res) =>
        logger.info(s"Completed metrics for ${metricsTool.name} with ${res.size} results")
      case Failure(e) =>
        logger.error(e)(s"Failed metrics for ${metricsTool.name}")
    }

    result.map(_.to[Set])
  }

}

object CodacyPluginsAnalyser extends AnalyserCompanion[Try] {

  val name: String = "codacy-plugins"

  private val allToolShortNames = Tool.allToolShortNames

  private val internetToolShortNames = Tool.internetToolShortNames

  override def apply(): Analyser[Try] = new CodacyPluginsAnalyser()

  object errors {

    def missingTool(tool: String): String = {
      if (internetToolShortNames.contains(tool)) {
        s"The tool $tool needs network access to execute. Run with the parameter 'allow-network'."
      } else {
        s"Could not find tool $tool in (${allToolShortNames.mkString(", ")})"
      }
    }
  }

}
