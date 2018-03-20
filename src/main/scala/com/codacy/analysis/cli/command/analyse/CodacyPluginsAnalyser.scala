package com.codacy.analysis.cli.command.analyse

import better.files.File
import com.codacy.analysis.cli.model._
import plugins.results.interface.scala.{PluginConfiguration, PluginRequest}
import plugins.results.traits.IDockerPlugin
import utils.PluginHelper

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class CodacyPluginsAnalyser extends Analyser[Try] {

  override def analyse(tool: String, file: File, config: Configuration): Try[Set[Result]] = {
    val directory = if (file.isDirectory) file else file.parent
    val filesToAnalyse = if (file.isDirectory) file.listRecursively else Set(file)
    // TODO: Do config parsing
    for {
      tool <- findTool(tool)
      request = PluginRequest(
        directory.pathAsString,
        filesToAnalyse.to[List].map(_.pathAsString),
        PluginConfiguration(None, None))
      res <- tool.run(request, Option(10.minutes))
    } yield {
      (res.results.map(r => Issue(LineLocation(r.line), r.filename))(collection.breakOut): Set[Result]) ++
        res.failedFiles.map(fe => FileError(fe, "Failed to analyse file."))
    }
  }

  private def findTool(tool: String): Try[IDockerPlugin] = {
    PluginHelper.dockerPlugins
      .find(_.shortName.equalsIgnoreCase(tool))
      .fold[Try[IDockerPlugin]](Failure(CodacyPluginsAnalyser.errors.missingTool(tool)))(Success(_))
  }

}

object CodacyPluginsAnalyser extends AnalyserCompanion[Try] {

  val name: String = "codacy-plugins"

  private val allToolShotNames = PluginHelper.dockerPlugins.map(_.shortName)

  override def apply(): Analyser[Try] = new CodacyPluginsAnalyser()

  object errors {

    def missingTool(tool: String): Exception = {
      new Exception(s"Could not find tool for name=$tool in (${allToolShotNames.mkString(", ")})")
    }
  }

}
