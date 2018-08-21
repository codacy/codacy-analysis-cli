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
        SetOps.mapInParallel[ITool, ExecutorResult](tools, nrParallelTools) { tool: ITool =>
          tool match {
            case tool: Tool =>
              val analysisResults = issues(tool, filesTarget, localConfigurationFile)
              analysisResults.foreach(results => formatter.addAll(results.to[List]))
              IssuesToolExecutorResult(tool.name, filesTarget.readableFiles, analysisResults)
            case metricsTool: MetricsTool =>
              val analysisResults = metrics(metricsTool, filesTarget, localConfigurationFile)
              analysisResults.foreach(results => formatter.addAll(results.to[List]))
              MetricsToolExecutorResult(metricsTool.languageToRun.name, filesTarget.readableFiles, analysisResults)
            case duplicationTool: DuplicationTool =>
              val analysisResults = duplication(duplicationTool, filesTarget, localConfigurationFile)
              analysisResults.foreach(results => formatter.addAll(results.to[List]))
              DuplicationToolExecutorResult(
                duplicationTool.languageToRun.name,
                filesTarget.readableFiles,
                analysisResults)
          }
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

  private def duplication(
    duplicationTool: DuplicationTool,
    filesTarget: FilesTarget,
    localConfigurationFile: Either[String, CodacyConfigurationFile]): Try[Set[DuplicationClone]] = {

    val duplicationFilesTarget =
      fileCollector.filter(duplicationTool, filesTarget, localConfigurationFile, remoteProjectConfiguration)

    analyser.duplication(duplicationTool, duplicationFilesTarget.directory, duplicationFilesTarget.readableFiles)
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
               allowNetwork: Boolean): Either[String, Set[ITool]] = {

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
