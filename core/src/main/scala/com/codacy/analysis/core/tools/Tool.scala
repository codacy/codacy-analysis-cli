package com.codacy.analysis.core.tools

import java.nio.file.{Path, Paths}

import better.files.File
import com.codacy.analysis.core.analysis.{Analyser, CodacyPluginsAnalyser}
import com.codacy.analysis.core.model.{Configuration, Issue, _}
import com.codacy.analysis.core.utils.FileHelper
import com.codacy.plugins.api
import com.codacy.plugins.api.languages.Language
import com.codacy.plugins.api.results
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.results.traits.{DockerTool, DockerToolDocumentation, DockerToolWithConfig, ToolRunner}
import com.codacy.plugins.results.{PatternRequest, PluginConfiguration, PluginRequest}
import com.codacy.plugins.traits.{BinaryDockerRunner, DockerRunner}
import com.codacy.plugins.utils.PluginHelper
import org.log4s.{Logger, getLogger}
import play.api.libs.json.JsValue

import scala.concurrent.duration._
import scala.util.Try

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

class Tool(runner: ToolRunner, defaultRunTimeout: Duration)(private val plugin: DockerTool, val languageToRun: Language)
    extends ITool {

  private val logger: Logger = getLogger

  override def name: String = plugin.shortName
  def uuid: String = plugin.uuid

  def needsPatternsToRun: Boolean = plugin.needsPatternsToRun
  def allowsUIConfiguration: Boolean = plugin.hasUIConfiguration

  override def supportedLanguages: Set[Language] = plugin.languages

  def configFilenames: Set[String] = plugin match {
    case plugin: DockerToolWithConfig =>
      plugin.configFilename.to[Set]
    case _ =>
      Set.empty[String]
  }

  def run(directory: File,
          files: Set[Path],
          config: Configuration,
          timeout: Option[Duration] = Option.empty[Duration]): Try[Set[ToolResult]] = {
    val pluginConfiguration = config match {
      case CodacyCfg(patterns, _, extraValues) =>
        val pts: List[PatternRequest] = patterns.map { pt =>
          val pms: Map[String, String] = pt.parameters.map(pm => (pm.name, pm.value))(collection.breakOut)
          PatternRequest(pt.id, pms)
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

    runner.run(request, timeout.getOrElse(defaultRunTimeout)).map { res =>
      (res.results.map(r =>
        Issue(
          results.Pattern.Id(r.patternIdentifier),
          FileHelper.relativePath(sourceDirectory.appendPrefix(r.filename)),
          Issue.Message(r.message),
          r.level,
          r.category,
          LineLocation(r.line)))(collection.breakOut): Set[ToolResult]) ++
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
    options: Option[Map[String, JsValue]]): Option[Map[api.Options.Key, api.Options.Value]] = {
    options.map(_.map {
      case (k, v) => api.Options.Key(k) -> api.Options.Value(v)
    })
  }

}

object Tool {

  val availableTools: List[DockerTool] = PluginHelper.dockerPlugins

  val internetToolShortNames: Set[String] =
    PluginHelper.dockerEnterprisePlugins.map(_.shortName)(collection.breakOut)

  val allToolShortNames: Set[String] = internetToolShortNames ++ availableTools.map(_.shortName)

  def apply(plugin: DockerTool, languageToRun: Language): Tool = {
    val dockerRunner = new BinaryDockerRunner[Result](plugin)()
    val runner = new ToolRunner(plugin, new DockerToolDocumentation(plugin), dockerRunner)
    new Tool(runner, DockerRunner.defaultRunTimeout)(plugin, languageToRun)
  }
}

class ToolCollector(allowNetwork: Boolean) {

  private val logger: Logger = getLogger

  private val availableInternetTools: List[DockerTool] = if (allowNetwork) {
    PluginHelper.dockerEnterprisePlugins
  } else {
    List.empty[DockerTool]
  }

  private val availableTools: List[DockerTool] = Tool.availableTools ++ availableInternetTools

  def fromUuid(uuid: String): Option[DockerTool] = {
    availableTools.find(_.uuid == uuid)
  }

  def fromNameOrUUID(toolInput: String, languages: Set[Language]): Either[Analyser.Error, Set[Tool]] = {
    from(toolInput, languages)
  }

  def fromToolUUIDs(toolUuids: Set[String], languages: Set[Language]): Either[Analyser.Error, Set[Tool]] = {
    if (toolUuids.isEmpty) {
      Left(Analyser.Error.NoActiveToolInConfiguration)
    } else {
      val toolsIdentified = toolUuids.flatMap { toolUuid =>
        from(toolUuid, languages).fold({ _ =>
          logger.warn(s"Failed to get tool for uuid:$toolUuid")
          Set.empty[Tool]
        }, identity)
      }

      if (toolsIdentified.size != toolUuids.size) {
        logger.warn("Some tools from remote configuration could not be found locally")
      }

      Right(toolsIdentified)
    }
  }

  def fromLanguages(languages: Set[Language]): Either[Analyser.Error, Set[Tool]] = {
    val collectedTools: Set[Tool] = (for {
      tool <- availableTools
      languagesToRun = tool.languages.intersect(languages)
      languageToRun <- languagesToRun
    } yield Tool(tool, languageToRun))(collection.breakOut)

    if (collectedTools.isEmpty) {
      Left(Analyser.Error.NoToolsFoundForFiles)
    } else {
      Right(collectedTools)
    }
  }

  def from(value: String, languages: Set[Language]): Either[Analyser.Error, Set[Tool]] = {
    find(value).map(dockerTool => dockerTool.languages.intersect(languages).map(Tool(dockerTool, _)))
  }

  private def find(value: String): Either[Analyser.Error, DockerTool] = {
    availableTools
      .find(p => p.shortName.equalsIgnoreCase(value) || p.uuid.equalsIgnoreCase(value))
      .toRight(CodacyPluginsAnalyser.errors.missingTool(value))
  }

}
