package com.codacy.analysis.cli.command.analyse

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.cli.command.Properties
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor._
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.core.analysis.Analyser
import com.codacy.analysis.core.clients.api.ProjectConfiguration
import com.codacy.analysis.core.configuration.CodacyConfigurationFile
import com.codacy.analysis.core.converters.ConfigurationHelper
import com.codacy.analysis.core.files.{FileCollector, FilesTarget}
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.tools._
import com.codacy.analysis.core.utils.InheritanceOps.InheritanceOps
import com.codacy.analysis.core.utils.TryOps._
import com.codacy.analysis.core.utils.{LanguagesHelper, SetOps}
import com.codacy.plugins.api.languages.Language
import org.log4s.{Logger, getLogger}
import play.api.libs.json.JsValue

import scala.concurrent.duration.Duration
import scala.sys.process.Process
import scala.util.{Failure, Success, Try}

class AnalyseExecutor(toolInput: Option[String],
                      directory: Option[File],
                      formatter: Formatter,
                      analyser: Analyser[Try],
                      fileCollector: FileCollector[Try],
                      remoteProjectConfiguration: Either[String, ProjectConfiguration],
                      nrParallelTools: Option[Int],
                      allowNetwork: Boolean,
                      forceFilePermissions: Boolean,
                      toolTimeout: Option[Duration] = Option.empty[Duration]) {

  private val logger: Logger = getLogger

  def run(): Either[String, Seq[ExecutorResult]] = {
    formatter.begin()

    val baseDirectory =
      directory.fold(Properties.codacyCode.getOrElse(File.currentWorkingDirectory))(dir =>
        if (dir.isDirectory) dir else dir.parent)

    val localConfigurationFile: Either[String, CodacyConfigurationFile] =
      CodacyConfigurationFile.search(baseDirectory).flatMap(CodacyConfigurationFile.load)

    if (forceFilePermissions) {
      overrideFilePermissions(baseDirectory)
    }

    val filesTargetAndTool: Either[String, (FilesTarget, Set[ITool])] = for {
      filesTarget <- fileCollector
        .list(baseDirectory, localConfigurationFile, remoteProjectConfiguration)
        .toRight("Could not access project files")
      tools <- allTools(
        toolInput,
        remoteProjectConfiguration,
        LanguagesHelper.fromFileTarget(filesTarget, localConfigurationFile),
        allowNetwork)
    } yield (filesTarget, tools)

    val analysisResult: Either[String, Seq[ExecutorResult]] = filesTargetAndTool.map {
      case (filesTarget, tools) =>
        SetOps.mapInParallel(tools, nrParallelTools) { tool: ITool =>
          val analysisResults: Try[Set[Result]] = tool match {
            case tool: Tool =>
              issues(tool, filesTarget, localConfigurationFile).map(_.map(_.to[Result]))
            case metricsTool: MetricsTool =>
              metrics(metricsTool, filesTarget, localConfigurationFile).map(_.map(_.to[Result]))
            case _: DuplicationTool =>
              Try(Set.empty)
          }
          analysisResults.foreach(results => formatter.addAll(results.to[List]))

          ExecutorResult(tool.name, filesTarget.readableFiles, analysisResults)

        }
    }

    formatter.end()

    analysisResult
  }

  private def issues(tool: Tool,
                     filesTarget: FilesTarget,
                     localConfigurationFile: Either[String, CodacyConfigurationFile]): Try[Set[ToolResult]] = {
    val analysisFilesTarget =
      fileCollector.filter(tool, filesTarget, localConfigurationFile, remoteProjectConfiguration)

    val toolHasConfigFiles = fileCollector.hasConfigurationFiles(tool, filesTarget)

    for {
      toolConfiguration <- getToolConfiguration(
        tool,
        toolHasConfigFiles,
        localConfigurationFile,
        remoteProjectConfiguration)
      results <- analyser
        .analyse(tool, analysisFilesTarget.directory, analysisFilesTarget.readableFiles, toolConfiguration, toolTimeout)
    } yield results
  }

  private def metrics(metricsTool: MetricsTool,
                      filesTarget: FilesTarget,
                      localConfigurationFile: Either[String, CodacyConfigurationFile]): Try[Set[FileMetrics]] = {
    val metricsFilesTarget =
      fileCollector.filter(metricsTool, filesTarget, localConfigurationFile, remoteProjectConfiguration)

    analyser.metrics(metricsTool, metricsFilesTarget.directory, Some(metricsFilesTarget.readableFiles))
  }

  private def getToolConfiguration(tool: Tool,
                                   hasConfigFiles: Boolean,
                                   localConfiguration: Either[String, CodacyConfigurationFile],
                                   remoteConfiguration: Either[String, ProjectConfiguration]): Try[Configuration] = {
    val (baseSubDir, extraValues) = getExtraConfiguration(localConfiguration, tool)
    (for {
      projectConfig <- remoteConfiguration
      toolConfiguration <- projectConfig.toolConfiguration
        .find(_.uuid.equalsIgnoreCase(tool.uuid))
        .toRight[String](s"Could not find configuration for tool ${tool.name}")
    } yield {
      val shouldUseConfigFile = toolConfiguration.notEdited && hasConfigFiles
      val shouldUseRemoteConfiguredPatterns = !shouldUseConfigFile && tool.allowsUIConfiguration && toolConfiguration.patterns.nonEmpty
      // TODO: Review isEnabled condition when running multiple tools since we might want to force this for single tools
      // val shouldRun = toolConfiguration.isEnabled && (!tool.needsPatternsToRun || shouldUseConfigFile || shouldUseRemoteConfiguredPatterns)
      val shouldRun = !tool.needsPatternsToRun || shouldUseConfigFile || shouldUseRemoteConfiguredPatterns

      if (!shouldRun) {
        logger.error(s"""Could not find conditions to run tool ${tool.name} with:
             |shouldUseConfigFile:$shouldUseConfigFile = notEdited:${toolConfiguration.notEdited} && foundToolConfigFile:$hasConfigFiles
             |shouldUseRemoteConfiguredPatterns:$shouldUseRemoteConfiguredPatterns = !shouldUseConfigFile:$shouldUseConfigFile && allowsUIConfiguration:${tool.allowsUIConfiguration} && hasPatterns:${toolConfiguration.patterns.nonEmpty}
             |shouldRun:$shouldRun = !needsPatternsToRun:${tool.needsPatternsToRun} || shouldUseConfigFile:$shouldUseConfigFile || shouldUseRemoteConfiguredPatterns:$shouldUseRemoteConfiguredPatterns
           """.stripMargin)
        Failure(new Exception(s"Could not find conditions to run tool ${tool.name}"))
      } else if (shouldUseConfigFile) {
        logger.info(s"Preparing to run ${tool.name} with configuration file")
        Success(FileCfg(baseSubDir, extraValues))
      } else {
        logger.info(s"Preparing to run ${tool.name} with remote configuration")
        Success(
          CodacyCfg(
            toolConfiguration.patterns.map(ConfigurationHelper.apiPatternToInternalPattern),
            baseSubDir,
            extraValues))
      }
    }).right.getOrElse[Try[Configuration]] {
      logger.info(s"Preparing to run ${tool.name} with defaults")
      Success(FileCfg(baseSubDir, extraValues))
    }
  }

  private def getExtraConfiguration(localConfiguration: Either[String, CodacyConfigurationFile],
                                    tool: Tool): (Option[String], Option[Map[String, JsValue]]) = {
    (for {
      config <- localConfiguration.toOption
      engines <- config.engines
      engineConfig <- engines.get(tool.name)
    } yield engineConfig).fold {
      logger.info(s"Could not find local extra configuration for ${tool.name}")
      (Option.empty[String], Option.empty[Map[String, JsValue]])
    } { ec =>
      logger.info(s"Found local extra configuration for ${tool.name}")
      (ec.baseSubDir, ec.extraValues)
    }
  }

  private def overrideFilePermissions(sourceDirectory: File) = {
    Process(Seq("find", sourceDirectory.pathAsString, "-type", "d", "-exec", "chmod", "u+rwx", "{}", ";")).!
    Process(Seq("find", sourceDirectory.pathAsString, "-type", "d", "-exec", "chmod", "ugo+rx", "{}", ";")).!
    Process(Seq("find", sourceDirectory.pathAsString, "-type", "f", "-exec", "chmod", "u+rw", "{}", ";")).!
    Process(Seq("find", sourceDirectory.pathAsString, "-type", "f", "-exec", "chmod", "ugo+r", "{}", ";")).!
  }

}

object AnalyseExecutor {

  final case class ExecutorResult(toolName: String, files: Set[Path], analysisResults: Try[Set[Result]])

  def allTools(toolInput: Option[String],
               remoteProjectConfiguration: Either[String, ProjectConfiguration],
               languages: Set[Language],
               allowNetwork: Boolean) = {

    val metricsTools = MetricsToolCollector.fromLanguages(languages)

    val toolsEither = tools(toolInput, remoteProjectConfiguration, allowNetwork, languages)

    toolsEither.map(_ ++ metricsTools)
  }

  def tools(toolInput: Option[String],
            remoteProjectConfiguration: Either[String, ProjectConfiguration],
            allowNetwork: Boolean,
            languages: Set[Language]): Either[String, Set[Tool]] = {

    val toolCollector = new ToolCollector(allowNetwork)

    def fromRemoteConfig: Either[String, Set[Tool]] = {
      remoteProjectConfiguration.flatMap(projectConfiguration =>
        toolCollector.fromToolUUIDs(projectConfiguration.toolConfiguration.filter(_.isEnabled).map(_.uuid)))
    }

    def fromLocalConfig: Either[String, Set[Tool]] = {
      toolCollector.fromLanguages(languages)
    }

    toolInput.map { toolStr =>
      toolCollector.fromNameOrUUID(toolStr)
    }.getOrElse {
      for {
        e1 <- fromRemoteConfig.left
        e2 <- fromLocalConfig.left
      } yield s"$e1 and $e2"
    }
  }
}
