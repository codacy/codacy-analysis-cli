package com.codacy.analysis.cli.analysis

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.cli.model._
import com.codacy.analysis.cli.tools.Tool
import org.log4s.{Logger, getLogger}

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class CodacyPluginsAnalyser extends Analyser[Try] {

  private val logger: Logger = getLogger

  override def analyse(tool: Tool, directory: File, files: Set[Path], config: Configuration): Try[Set[Result]] = {
    val result = tool.run(directory, files, config, 10.minutes)

    result match {
      case Success(res) =>
        logger.info(s"Completed analysis for ${tool.name} with ${res.size} results")
      case Failure(e) =>
        logger.error(e)(s"Failed analysis for ${tool.name}")
    }

    result
  }

}

object CodacyPluginsAnalyser extends AnalyserCompanion[Try] {

  val name: String = "codacy-plugins"

  private val allToolShortNames = Tool.allToolShortNames

  override def apply(): Analyser[Try] = new CodacyPluginsAnalyser()

  object errors {

    def missingTool(tool: String): String = {
      s"Could not find tool $tool in (${allToolShortNames.mkString(", ")})"
    }
  }

}
