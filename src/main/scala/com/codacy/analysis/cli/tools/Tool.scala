package com.codacy.analysis.cli.tools

import java.nio.file.{Path, Paths}

import better.files.File
import codacy.docker.api
import com.codacy.analysis.cli.analysis.CodacyPluginsAnalyser
import com.codacy.analysis.cli.model.{Configuration, Issue, Result, _}
import com.codacy.analysis.cli.utils.FileHelper
import com.codacy.api.dtos.Language
import org.log4s.{Logger, getLogger}
import play.api.libs.json.JsValue
import plugins.results.interface.scala.{Pattern, PluginConfiguration, PluginRequest}
import plugins.results.traits.{IDockerPlugin, IDockerPluginConfig}
import utils.PluginHelper

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

sealed trait SourceDirectory {
  val sourceDirectory: String
  def appendPrefix(filename: String): String
  def removePrefix(filename: String): String
}

final case class Directory(sourceDirectory: String) extends SourceDirectory {
  def appendPrefix(filename: String): String = filename
  def removePrefix(filename: String): String = filename
}

final case class SubDirectory(sourceDirectory: String, protected val subDirectory: String) extends SourceDirectory {
  def appendPrefix(filename: String): String = subDirectory + java.io.File.separator + filename
  def removePrefix(filename: String): String = filename.stripPrefix(subDirectory).stripPrefix(java.io.File.separator)
}

class Tool(private val plugin: IDockerPlugin) {

  private val logger: Logger = getLogger

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
          files: Set[Path],
          config: Configuration,
          timeout: Duration = 10.minutes): Try[Set[Result]] = {
    val pluginConfiguration = config match {
      case CodacyCfg(patterns, _, extraValues) =>
        val pts: List[Pattern] = patterns.map { pt =>
          val pms: Map[String, String] = pt.parameters.map(pm => (pm.name, pm.value))(collection.breakOut)
          Pattern(pt.id, pms)
        }(collection.breakOut)
        PluginConfiguration(Option(pts), convertExtraValues(extraValues))

      case FileCfg(_, extraValues) =>
        PluginConfiguration(None, convertExtraValues(extraValues))
    }

    val sourceDirectory = getSourceDirectory(directory, config.baseSubDir)
    val request =
      PluginRequest(
        sourceDirectory.sourceDirectory,
        files.to[List].map(f => sourceDirectory.removePrefix(f.toString)),
        pluginConfiguration)

    plugin.run(request, Option(timeout)).map { res =>
      (res.results.map(r =>
        Issue(
          api.Pattern.Id(r.patternIdentifier),
          FileHelper.relativePath(sourceDirectory.appendPrefix(r.filename)),
          Issue.Message(r.message),
          r.level,
          r.category,
          LineLocation(r.line)))(collection.breakOut): Set[Result]) ++
        res.failedFiles.map(fe => FileError(FileHelper.relativePath(fe), "Failed to analyse file."))
    }
  }

  private def getSourceDirectory(directory: File, baseSubDir: Option[String]): SourceDirectory = {
    val baseSubDirPath = baseSubDir.map(Paths.get(_).normalize().toString)
    baseSubDirPath.fold[SourceDirectory] {
      logger.info(s"Using the root directory $directory to run")
      Directory(directory.pathAsString)
    } { path =>
      val subDir = directory / path
      if (isSubFolder(subDir, directory)) {
        logger.info(s"Using the sub directory $subDir to run")
        SubDirectory(subDir.path.normalize().toString, path)
      } else {
        logger.warn(s"The directory $subDir is not below the root directory")
        Directory(directory.pathAsString)
      }
    }
  }

  private def isSubFolder(subDirPath: File, parentPath: File): Boolean = {
    subDirPath.path.normalize().toString.startsWith(parentPath.path.normalize().toString)
  }

  private implicit def convertExtraValues(
    options: Option[Map[String, JsValue]]): Option[Map[api.Configuration.Key, api.Configuration.Value]] = {
    options.map(_.map {
      case (k, v) => api.Configuration.Key(k) -> api.Configuration.Value(v)
    })
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
