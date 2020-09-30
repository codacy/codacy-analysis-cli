package com.codacy.analysis.core.tools

import java.nio.file.{Path, Paths}

import better.files.File
import com.codacy.analysis.core.analysis.CodacyPluginsAnalyser
import com.codacy.analysis.core.model.{Configuration, Issue, ParameterSpec, _}
import com.codacy.analysis.core.utils.FileHelper
import com.codacy.plugins.api
import com.codacy.plugins.api.languages.Language
import com.codacy.plugins.api.results
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.results.traits.ToolRunner
import com.codacy.plugins.results.{PatternRequest, PluginConfiguration, PluginRequest}
import com.codacy.plugins.runners.{BinaryDockerRunner, DockerInformation, DockerRunner}
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

  def removePrefix(filename: String): String =
    filename.stripPrefix(subDirectory).stripPrefix(java.io.File.separator)
}

class Tool(runner: ToolRunner, defaultRunTimeout: Duration)(private val tool: ToolSpec, val languageToRun: Language)
    extends ITool {

  private val logger: Logger = getLogger

  override def name: String = tool.shortName
  def uuid: String = tool.uuid

  override def supportedLanguages: Set[Language] = tool.languages

  def configFilenames: Set[String] =
    if (tool.hasConfigFile) tool.configFilenames else Set.empty

  def run(directory: File,
          files: Set[Path],
          config: Configuration,
          tmpDirectory: Option[File] = None,
          timeout: Option[Duration] = Option.empty[Duration]): Try[Set[ToolResult]] = {
    val pluginConfiguration = config match {
      case CodacyCfg(patterns, _, extraValues) =>
        val pts: List[PatternRequest] = patterns.map { pt =>
          val pms: Map[String, String] =
            pt.parameters.map(pm => (pm.name, pm.value))(collection.breakOut)
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

    runner.run(request, timeout.getOrElse(defaultRunTimeout), configTmpDirectory = tmpDirectory.map(_.toJava)).map {
      res =>
        (res.results.map(r =>
          Issue(
            results.Pattern.Id(r.patternIdentifier),
            FileHelper.relativePath(sourceDirectory.appendPrefix(r.filename)),
            Issue.Message(r.message),
            r.level,
            r.category,
            LineLocation(r.line)))(collection.breakOut): Set[ToolResult]) ++
          res.fileErrors.map(
            fe =>
              FileError(
                FileHelper.relativePath(sourceDirectory.appendPrefix(fe.filename)),
                fe.message.getOrElse("Failed to analyze file.")))
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

  def apply(fullToolSpec: FullToolSpec, languageToRun: Language): Tool = {
    val dockerInformation = new DockerInformation(
      dockerImageName = fullToolSpec.tool.dockerImage,
      needsCompilation = fullToolSpec.tool.needsCompilation)

    val dockerRunner = new BinaryDockerRunner[Result](dockerInformation)

    val runner =
      new ToolRunner(Option(fullToolSpec.toolApiSpec), fullToolSpec.tool.prefix, dockerRunner)
    new Tool(runner, DockerRunner.defaultRunTimeout)(fullToolSpec.tool, languageToRun)
  }
}

class ToolCollector(toolRepository: ToolRepository) {

  private val logger: Logger = getLogger

  def fromUuid(uuid: String): Either[AnalyserError, FullToolSpec] = {
    for {
      tool <- toolRepository.get(uuid)
      patterns <- toolRepository.listPatterns(tool.uuid)
    } yield {
      FullToolSpec(tool, patterns)
    }
  }

  def fromNameOrUUID(toolInput: String, languages: Set[Language]): Either[AnalyserError, Set[Tool]] = {
    from(toolInput, languages)
  }

  def fromToolUUIDs(toolUuids: Set[String], languages: Set[Language]): Either[AnalyserError, Set[Tool]] = {
    if (toolUuids.isEmpty) {
      Left(AnalyserError.NoActiveToolInConfiguration)
    } else {
      val toolsIdentified = toolUuids.flatMap { toolUuid =>
        from(toolUuid, languages).fold(
          { _ =>
            logger.warn(s"Failed to get tool for uuid:$toolUuid")
            Set.empty[Tool]
          },
          identity)
      }

      if (toolsIdentified.size != toolUuids.size) {
        logger.warn("Some tools from remote configuration could not be found locally")
      }

      Right(toolsIdentified)
    }
  }

  def from(value: String, languages: Set[Language]): Either[AnalyserError, Set[Tool]] = {
    for {
      tool <- find(value)
      patterns <- toolRepository.listPatterns(tool.uuid)
    } yield {
      tool.languages.intersect(languages).map(language => Tool(FullToolSpec(tool, patterns), language))
    }
  }

  private def find(value: String): Either[AnalyserError, ToolSpec] = {
    toolRepository.list().flatMap { availableTools =>
      availableTools
        .find(tool => tool.shortName.equalsIgnoreCase(value) || tool.uuid.equalsIgnoreCase(value))
        .toRight(CodacyPluginsAnalyser.errors.missingTool(value))
    }
  }

  def fromLanguages(languages: Set[Language]): Either[AnalyserError, Set[Tool]] = {
    val collectedTools: Set[Tool] = (for {
      //TODO: Handle and propagate the error instead of Seq.empty
      tool <- toolRepository.list().getOrElse(Seq.empty)
      //TODO: Handle and propagate the error instead of .toOption.toSeq
      patterns <- toolRepository.listPatterns(tool.uuid).toOption.toSeq
      languagesToRun = tool.languages.intersect(languages)
      languageToRun <- languagesToRun
    } yield Tool(FullToolSpec(tool, patterns), languageToRun))(collection.breakOut)

    if (collectedTools.isEmpty) {
      Left(AnalyserError.NoToolsFoundForFiles)
    } else {
      Right(collectedTools)
    }
  }

}
