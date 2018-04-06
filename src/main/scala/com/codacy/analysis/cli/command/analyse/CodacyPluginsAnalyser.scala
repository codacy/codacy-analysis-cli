package com.codacy.analysis.cli.command.analyse

import better.files.File
import codacy.docker.api
import com.codacy.analysis.cli.model._
import com.codacy.analysis.cli.utils.FileHelper
import plugins.results.interface.scala.{Pattern, PluginConfiguration, PluginRequest}
import plugins.results.traits.IDockerPlugin
import utils.PluginHelper

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class CodacyPluginsAnalyser extends Analyser[Try] {

  override def analyse(tool: String, directory: File, files: Set[File], config: Configuration): Try[Set[Result]] = {
    val pluginConfiguration = config match {
      case CodacyCfg(patterns) =>
        val pts: List[Pattern] = patterns.map { pt =>
          val pms: Map[String, String] = pt.parameters.map(pm => (pm.name, pm.value))(collection.breakOut)
          Pattern(pt.id, pms)
        }(collection.breakOut)
        PluginConfiguration(Option(pts), None)

      case FileCfg =>
        PluginConfiguration(None, None)
    }

    val request =
      PluginRequest(directory.pathAsString, files.to[List].map(_.pathAsString), pluginConfiguration)

    for {
      tool <- findTool(tool)
      res <- tool.run(request, Option(10.minutes))
    } yield {
      (res.results.map(
        r =>
          Issue(
            api.Pattern.Id(r.patternIdentifier),
            FileHelper.relativePath(r.filename),
            Issue.Message(r.message),
            r.level,
            r.category,
            LineLocation(r.line)))(collection.breakOut): Set[Result]) ++
        res.failedFiles.map(fe => FileError(FileHelper.relativePath(fe), "Failed to analyse file."))
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

  private val allToolShortNames = PluginHelper.dockerEnterprisePlugins.map(_.shortName)

  override def apply(): Analyser[Try] = new CodacyPluginsAnalyser()

  object errors {

    def missingTool(tool: String): Exception = {
      new Exception(s"Could not find tool for name=$tool in (${allToolShortNames.mkString(", ")})")
    }
  }

}
