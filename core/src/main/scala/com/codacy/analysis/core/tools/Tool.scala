package com.codacy.analysis.core.tools

import java.nio.file.{Path, Paths}

import better.files.File
import com.codacy.analysis.core.clients.{CodacyTool, CodacyToolPattern}
import com.codacy.analysis.core.model.{Configuration, Issue, _}
import com.codacy.analysis.core.utils.FileHelper
import com.codacy.plugins.api
import com.codacy.plugins.api.languages.Language
import com.codacy.plugins.api.{ParameterDescription, PatternDescription, results}
import com.codacy.plugins.api.results.Result
import com.codacy.plugins.results.traits.{CodacyToolDocumentation, DockerTool, ToolRunner}
import com.codacy.plugins.results.{PatternRequest, PluginConfiguration, PluginRequest}
import com.codacy.plugins.runners.{BinaryDockerRunner, DockerRunner}
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

  def removePrefix(filename: String): String =
    filename.stripPrefix(subDirectory).stripPrefix(java.io.File.separator)
}

class Tool(runner: ToolRunner, defaultRunTimeout: Duration)(private val plugin: DockerTool, val languageToRun: Language)
    extends ITool {

  private val logger: Logger = getLogger

  override def name: String = plugin.shortName
  def uuid: String = plugin.uuid

  override def supportedLanguages: Set[Language] = plugin.languages

  def configFilenames: Set[String] =
    if (plugin.hasConfigFile) plugin.configFilename.toSet else Set.empty

  def run(directory: File,
          files: Set[Path],
          config: Configuration,
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

    runner.run(request, timeout.getOrElse(defaultRunTimeout)).map { res =>
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
              fe.message.getOrElse("Failed to analyse file.")))
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

class CodacyDockerTool(dockerName: String,
                       isDefault: Boolean,
                       languages: Set[Language],
                       name: String,
                       shortName: String,
                       uuid: String,
                       documentationUrl: String,
                       sourceCodeUrl: String,
                       prefix: String = "",
                       needsCompilation: Boolean = false,
                       configFilename: Seq[String] = Seq.empty,
                       isClientSide: Boolean = false,
                       hasUIConfiguration: Boolean = true)
    extends DockerTool(
      dockerName,
      isDefault,
      languages,
      name,
      shortName,
      uuid,
      documentationUrl,
      sourceCodeUrl,
      prefix,
      needsCompilation,
      configFilename,
      isClientSide,
      hasUIConfiguration)

object Tool {

  def apply(codacyTool: CodacyTool, codacyPatterns: Seq[CodacyToolPattern], languageToRun: Language): Tool = {
    val codacyDockerTool = new CodacyDockerTool(
      dockerName = codacyTool.dockerImage,
      isDefault = codacyTool.enabledByDefault,
      languages = Set.empty,
      name = codacyTool.name,
      shortName = codacyTool.shortName,
      uuid = codacyTool.uuid,
      documentationUrl = codacyTool.documentationUrl.getOrElse(""),
      sourceCodeUrl = codacyTool.sourceCodeUrl.getOrElse(""),
      prefix = codacyTool.prefix.getOrElse(""),
      needsCompilation = codacyTool.needsCompilation,
      configFilename = codacyTool.configFilenames,
      isClientSide = codacyTool.clientSide,
      hasUIConfiguration = codacyTool.configurable)

    val patternsDescriptions = codacyPatterns.map { pattern =>
      val parameters = pattern.parameters.map { parameter =>
        ParameterDescription(results.Parameter.Name(parameter.name), parameter.description.getOrElse(""))
      }.toSet
      PatternDescription(
        results.Pattern.Id(pattern.id),
        pattern.title,
        Some(parameters),
        pattern.description,
        pattern.timeToFix,
        pattern.shortDescription)
    }.toSet

    val patternsSpecification = codacyPatterns.map { pattern =>
      val patternLevel = Result.Level.withName(pattern.level)
      val parameters = pattern.parameters.map { parameter =>
        results.Parameter.Specification(results.Parameter.Name(parameter.name), results.Parameter.Value(""))
      }.toSet

      results.Pattern.Specification(
        results.Pattern.Id(pattern.id),
        patternLevel,
        results.Pattern.Category.withName(pattern.category),
        pattern.subCategory.map(results.Pattern.Subcategory.withName),
        Some(parameters),
        languages = None)
    }.toSet

    val toolSpecification = results.Tool.Specification(
      results.Tool.Name(codacyTool.name),
      Some(results.Tool.Version(codacyTool.version)),
      patternsSpecification)

    val codacyToolDocumentation =
      new CodacyToolDocumentation(Some(toolSpecification), Some(patternsDescriptions), codacyTool.description)

    val dockerRunner = new BinaryDockerRunner[Result](codacyDockerTool)
    val runner =
      new ToolRunner(codacyDockerTool, codacyToolDocumentation, dockerRunner)
    new Tool(runner, DockerRunner.defaultRunTimeout)(codacyDockerTool, languageToRun)
  }
}
