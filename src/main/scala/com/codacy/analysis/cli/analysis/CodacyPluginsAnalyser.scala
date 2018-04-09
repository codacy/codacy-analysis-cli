package com.codacy.analysis.cli.analysis

import better.files.File
import com.codacy.analysis.cli.model._
import com.codacy.analysis.cli.tools.Tool

import scala.concurrent.duration._
import scala.util.Try

class CodacyPluginsAnalyser extends Analyser[Try] {

  override def analyse(tool: Tool, directory: File, files: Set[File], config: Configuration): Try[Set[Result]] = {
    tool.run(directory, files, config, 10.minutes)
  }

}

object CodacyPluginsAnalyser extends AnalyserCompanion[Try] {

  val name: String = "codacy-plugins"

  private val allToolShortNames = Tool.allToolShortNames

  override def apply(): Analyser[Try] = new CodacyPluginsAnalyser()

  object errors {

    def missingTool(tool: String): Exception = {
      new Exception(s"Could not find tool $tool in (${allToolShortNames.mkString(", ")})")
    }
  }

}
