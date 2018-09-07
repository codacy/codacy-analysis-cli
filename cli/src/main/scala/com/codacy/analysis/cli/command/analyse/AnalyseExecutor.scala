package com.codacy.analysis.cli.command.analyse

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.cli.CLIError
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
import com.codacy.analysis.core.utils.SeqOps._
import com.codacy.analysis.core.utils.TryOps._
import com.codacy.analysis.core.utils.{LanguagesHelper, SetOps}
import com.codacy.plugins.api.languages.Language
import org.log4s.{Logger, getLogger}
import play.api.libs.json.JsValue

import scala.concurrent.duration.Duration
import scala.sys.process.Process
import scala.util.{Failure, Success, Try}

class AnalyseExecutor(toolInput: Option[String],
                      directory: File,
                      formatter: Formatter,
                      analyser: Analyser[Try],
                      fileCollector: FileCollector[Try],
                      remoteProjectConfiguration: Either[String, ProjectConfiguration],
                      nrParallelTools: Option[Int],
                      allowNetwork: Boolean,
                      forceFilePermissions: Boolean,
                      toolTimeout: Option[Duration] = Option.empty[Duration]) {

  private val logger: Logger = getLogger

  def run(): Either[CLIError, Seq[ExecutorResult]] = {
    formatter.begin()

    val localConfigurationFile: Either[String, CodacyConfigurationFile] =
      CodacyConfigurationFile.search(directory).flatMap(CodacyConfigurationFile.load)

    if (forceFilePermissions) {
      overrideFilePermissions(directory)
    }

    val filesTargetAndTool: Either[CLIError, (FilesTarget, Set[ITool])] = for {
      filesTarget <- fileCollector
        .list(directory, localConfigurationFile, remoteProjectConfiguration)
        .toRight(CLIError.FilesAccessError)
      tools <- allTools(
        toolInput,
        remoteProjectConfiguration,
        LanguagesHelper.fromFileTarget(filesTarget, localConfigurationFile),
        allowNetwork)
    } yield (filesTarget, tools)

    val analysisResult: Either[CLIError, Seq[ExecutorResult]] = filesTargetAndTool.map {
      case (allFiles, tools) =>
        SetOps.mapInParallel[ITool, ExecutorResult](tools, nrParallelTools) { tool: ITool =>
          val filteredFiles: FilesTarget =
            fileCollector.filter(tool, allFiles, localConfigurationFile, remoteProjectConfiguration)

          tool match {
            case tool: Tool =>
              val analysisResults = issues(tool, filteredFiles, localConfigurationFile)
              analysisResults.foreach(results => formatter.addAll(results.to[List]))
              IssuesToolExecutorResult(tool.name, filteredFiles.readableFiles, analysisResults)
            case metricsTool: MetricsTool =>
              val analysisResults =
                analyser.metrics(metricsTool, filteredFiles.directory, Some(filteredFiles.readableFiles))
              analysisResults.foreach(results => formatter.addAll(results.to[List]))
              MetricsToolExecutorResult(metricsTool.languageToRun.name, filteredFiles.readableFiles, analysisResults)
            case duplicationTool: DuplicationTool =>
              val analysisResults =
                analyser.duplication(duplicationTool, filteredFiles.directory, filteredFiles.readableFiles)
              analysisResults.foreach(results => formatter.addAll(results.to[List]))
              DuplicationToolExecutorResult(
                duplicationTool.languageToRun.name,
                filteredFiles.readableFiles,
                analysisResults)
          }
        }
    }

    formatter.end()

    analysisResult.map { result =>
      val (metricsResults, issuesResults, duplicationResults) =
        result.partitionSubtypes[MetricsToolExecutorResult, IssuesToolExecutorResult, DuplicationToolExecutorResult]
      MetricsToolExecutor.reduceMetricsToolResultsByFile(metricsResults) ++ issuesResults ++ duplicationResults
    }
  }

  private def issues(tool: Tool,
                     analysisFilesTarget: FilesTarget,
                     localConfigurationFile: Either[String, CodacyConfigurationFile]): Try[Set[ToolResult]] = {

    val toolHasConfigFiles = fileCollector.hasConfigurationFiles(tool, analysisFilesTarget)

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

  sealed trait ExecutorResult
  final case class IssuesToolExecutorResult(toolName: String, files: Set[Path], analysisResults: Try[Set[ToolResult]])
      extends ExecutorResult
  final case class MetricsToolExecutorResult(language: String, files: Set[Path], analysisResults: Try[Set[FileMetrics]])
      extends ExecutorResult
  final case class DuplicationToolExecutorResult(language: String,
                                                 files: Set[Path],
                                                 analysisResults: Try[Set[DuplicationClone]])
      extends ExecutorResult

  def allTools(toolInput: Option[String],
               remoteProjectConfiguration: Either[String, ProjectConfiguration],
               languages: Set[Language],
               allowNetwork: Boolean): Either[CLIError, Set[ITool]] = {

    def metricsTools = MetricsToolCollector.fromLanguages(languages)
    def duplicationTools = DuplicationToolCollector.fromLanguages(languages)

    toolInput match {
      case None =>
        val toolsEither = tools(toolInput, remoteProjectConfiguration, allowNetwork, languages)

        toolsEither.map(_ ++ metricsTools ++ duplicationTools)

      case Some("metrics") =>
        Right(metricsTools.map(_.to[ITool]))

      case Some("duplication") =>
        Right(duplicationTools.map(_.to[ITool]))

      case Some(_) =>
        val toolsEither = tools(toolInput, remoteProjectConfiguration, allowNetwork, languages)
        toolsEither.map(_.map(_.to[ITool]))
    }
  }

  def tools(toolInput: Option[String],
            remoteProjectConfiguration: Either[String, ProjectConfiguration],
            allowNetwork: Boolean,
            languages: Set[Language]): Either[CLIError, Set[Tool]] = {

    val remoteProjectConfig =
      remoteProjectConfiguration.left.map(CLIError.NoRemoteProjectConfiguration)

    val toolCollector = new ToolCollector(allowNetwork)

    def fromRemoteConfig: Either[CLIError, Set[Tool]] = {
      remoteProjectConfig.flatMap { projectConfiguration =>
        val toolUuids = projectConfiguration.toolConfiguration.filter(_.isEnabled).map(_.uuid)
        toolCollector
          .fromToolUUIDs(toolUuids, languages)
          .left
          .map(_ => CLIError.NonExistentToolsFromRemoteConfiguration(toolUuids))
      }
    }

    def fromLocalConfig: Either[CLIError, Set[Tool]] = {
      toolCollector.fromLanguages(languages).left.map(CLIError.from)
    }

    toolInput.map { toolStr =>
      toolCollector.fromNameOrUUID(toolStr, languages).left.map(CLIError.from)
    }.getOrElse {
      for {
        e1 <- fromRemoteConfig.left
        e2 <- fromLocalConfig.left
      } yield CLIError.CouldNotGetTools(s"${e1.message} and ${e2.message}")
    }
  }
}
