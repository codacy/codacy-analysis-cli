package com.codacy.analysis.cli.tools

import better.files.File
import codacy.docker.api
import com.codacy.analysis.cli.analysis.CodacyPluginsAnalyser
import com.codacy.analysis.cli.model.{Issue, Result, _}
import com.codacy.analysis.cli.utils.FileHelper
import com.codacy.api.dtos.Language
import plugins.results.interface.scala.{Pattern, PluginConfiguration, PluginRequest}
import plugins.results.traits.{IDockerPlugin, IDockerPluginConfig}
import utils.PluginHelper

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class Tool(private val plugin: IDockerPlugin) {
  def name: String = plugin.shortName
  def uuid: String = plugin.uuid

  def languages: Set[Language] = plugin.languages

  def configFilenames: Set[String] = plugin match {
    case plugin: IDockerPluginConfig =>
      plugin.configFilename.to[Set]
    case _ =>
      Set.empty[String]
  }

  def run(directory: File,
          files: Set[File],
          config: Configuration,
          timeout: Duration = 10.minutes): Try[Set[Result]] = {
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

    plugin.run(request, Option(timeout)).map { res =>
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
}

object Tool {

  private val allTools = PluginHelper.dockerPlugins.++(PluginHelper.dockerEnterprisePlugins)

  def allToolShortNames: Set[String] = allTools.map(_.shortName)(collection.breakOut)

  def from(value: String): Try[Tool] = findTool(value).map(new Tool(_))

  private def findTool(value: String): Try[IDockerPlugin] = {
    allTools
      .find(p => p.shortName.equalsIgnoreCase(value) || p.uuid.equalsIgnoreCase(value))
      .fold[Try[IDockerPlugin]](Failure(CodacyPluginsAnalyser.errors.missingTool(value)))(Success(_))
  }

}
