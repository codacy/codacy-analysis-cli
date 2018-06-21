package com.codacy.analysis.cli.tools

import java.nio.file.{Path, Paths}

import better.files.File
import com.codacy.analysis.cli.analysis.CodacyPluginsAnalyser
import com.codacy.analysis.cli.files.FilesTarget
import com.codacy.analysis.cli.model.{Configuration, Issue, Result, _}
import com.codacy.analysis.cli.utils.{EitherOps, FileHelper}
import com.codacy.plugins.api
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.api.results
import com.codacy.plugins.results.traits.{DockerTool, DockerToolWithConfig, ToolRunner}
import com.codacy.plugins.results.{PatternRequest, PluginConfiguration, PluginRequest}
import com.codacy.plugins.utils.PluginHelper
import org.log4s.{Logger, getLogger}
import play.api.libs.json.JsValue

import scala.concurrent.duration._
import scala.sys.process.Process
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

class Tool(private val plugin: DockerTool) {

  private val logger: Logger = getLogger

  def name: String = plugin.shortName
  def uuid: String = plugin.uuid

  def needsPatternsToRun: Boolean = plugin.needsPatternsToRun
  def allowsUIConfiguration: Boolean = plugin.hasUIConfiguration

  def languages: Set[Language] = plugin.languages

  def configFilenames: Set[String] = plugin match {
    case plugin: DockerToolWithConfig =>
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

    // HACK: Give default permissions to files so they can be read inside the docker
    overridePermissions(sourceDirectory)

    ToolRunner(plugin).run(request, Option(timeout)).map { res =>
      (res.results.map(r =>
        Issue(
          results.Pattern.Id(r.patternIdentifier),
          FileHelper.relativePath(sourceDirectory.appendPrefix(r.filename)),
          Issue.Message(r.message),
          r.level,
          r.category,
          LineLocation(r.line)))(collection.breakOut): Set[Result]) ++
        res.failedFiles.map(fe => FileError(FileHelper.relativePath(fe), "Failed to analyse file."))
    }
  }

  private def overridePermissions(sourceDirectory: SourceDirectory) = {
    Process(Seq("find", sourceDirectory.sourceDirectory, "-type", "d", "-exec", "chmod", "u+rwx", "{}", ";")).!
    Process(Seq("find", sourceDirectory.sourceDirectory, "-type", "d", "-exec", "chmod", "ugo+rx", "{}", ";")).!
    Process(Seq("find", sourceDirectory.sourceDirectory, "-type", "f", "-exec", "chmod", "u+rw", "{}", ";")).!
    Process(Seq("find", sourceDirectory.sourceDirectory, "-type", "f", "-exec", "chmod", "ugo+r", "{}", ";")).!
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

  val internetToolShortNames: Set[String] =
    PluginHelper.dockerEnterprisePlugins.map(_.shortName)(collection.breakOut)

  val allToolShortNames: Set[String] = internetToolShortNames ++ PluginHelper.dockerPlugins.map(_.shortName)
}

class ToolCollector(allowNetwork: Boolean) {

  private val availableInternetTools = if (allowNetwork) {
    PluginHelper.dockerEnterprisePlugins
  } else { List.empty[DockerTool] }

  private val availableTools = PluginHelper.dockerPlugins ++ availableInternetTools

  def fromNameOrUUID(toolInput: String): Either[String, Set[Tool]] = {
    from(toolInput).map(Set(_))
  }

  def fromToolUUIDs(toolUuids: Set[String]): Either[String, Set[Tool]] = {
    if (toolUuids.isEmpty) {
      Left("No active tool found on the remote configuration")
    } else {
      EitherOps.sequenceWithFixedLeft("A tool from remote configuration could not be found locally")(
        toolUuids.map(from))
    }
  }

  def fromFileTarget(filesTarget: FilesTarget,
                     languageCustomExtensions: List[(Language, Seq[String])]): Either[String, Set[Tool]] = {
    val fileLanguages = filesTarget.files.flatMap(path => Languages.forPath(path.toString, languageCustomExtensions))

    val collectedTools: Set[Tool] = availableTools.collect {
      case tool if fileLanguages.exists(tool.languages.contains) =>
        new Tool(tool)
    }(collection.breakOut)

    if (collectedTools.isEmpty) {
      Left("No tools found for files provided")
    } else {
      Right(collectedTools)
    }
  }

  def from(value: String): Either[String, Tool] = find(value).map(new Tool(_))

  private def find(value: String): Either[String, DockerTool] = {
    availableTools
      .find(p => p.shortName.equalsIgnoreCase(value) || p.uuid.equalsIgnoreCase(value))
      .toRight(CodacyPluginsAnalyser.errors.missingTool(value))
  }

}
