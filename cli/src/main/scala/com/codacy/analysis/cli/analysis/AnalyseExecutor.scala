package com.codacy.analysis.cli.analysis

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.cli.CLIError
import com.codacy.analysis.cli.configuration.CLIConfiguration
import com.codacy.analysis.cli.configuration.CLIConfiguration.IssuesTool.toInternalPattern
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.core.analysis.Analyser
import com.codacy.analysis.core.files.{FileCollector, FilesTarget}
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.tools._
import com.codacy.analysis.core.utils.IOHelper.IOThrowable
import com.codacy.analysis.core.utils.InheritanceOps.InheritanceOps
import com.codacy.analysis.core.utils.SeqOps._
import com.codacy.analysis.core.utils.{LanguagesHelper, SetOps}
import com.codacy.plugins.api.languages.Language
import org.log4s.{Logger, getLogger}
import play.api.libs.json.JsValue
import scalaz.zio.IO

import scala.sys.process.Process
import scala.util.{Failure, Success, Try}

class AnalyseExecutor(formatter: Formatter,
                      analyser: Analyser[IOThrowable],
                      fileCollector: FileCollector[IOThrowable],
                      configuration: CLIConfiguration.Analysis) {

  import com.codacy.analysis.cli.analysis.AnalyseExecutor._

  private val logger: Logger = getLogger

  def run(): IO[CLIError, Seq[ExecutorResult[_]]] = {

    if (configuration.forceFilePermissions) {
      overrideFilePermissions(configuration.projectDirectory)
    }

    val filesTargetAndTool: IO[CLIError, (FilesTarget, FilesTarget, Set[ITool])] = for {
      allFilesTarget <- fileCollector
        .list(configuration.projectDirectory)
        .bimap(_ => CLIError.FilesAccessError, identity)
      filesGlobalTarget = fileCollector.filterGlobal(allFilesTarget, configuration.fileExclusionRules)
      tools <- IO.fromEither(
        allTools(
          configuration.tool,
          configuration.toolConfiguration,
          LanguagesHelper
            .fromFileTarget(filesGlobalTarget, configuration.fileExclusionRules.allowedExtensionsByLanguage)))
    } yield (allFilesTarget, filesGlobalTarget, tools)

    val analysisResult: IO[CLIError, Seq[IO[Nothing, ExecutorResult[_]]]] = filesTargetAndTool.map {
      case (allFiles, globalFiles, tools) =>
        SetOps.mapInParallel[ITool, IO[Nothing, ExecutorResult[_]]](tools, configuration.parallel) { tool: ITool =>
          val filteredFiles: FilesTarget =
            fileCollector.filterTool(tool, globalFiles, configuration.fileExclusionRules)

          tool match {
            case tool: Tool =>
              val toolHasConfigFiles = fileCollector.hasConfigurationFiles(tool, allFiles)
              val analysisResults = issues(tool, filteredFiles, configuration.toolConfiguration, toolHasConfigFiles)

              val tryIO: IO[Nothing, Try[Set[ToolResult]]] =
                analysisResults.redeem(err => IO.point(Failure(err)), results => IO.point(Success(results)))

              tryIO.map(IssuesToolExecutorResult(tool.name, filteredFiles.readableFiles, _))
            case metricsTool: MetricsTool =>
              val analysisResults =
                analyser.metrics(metricsTool, filteredFiles.directory, Some(filteredFiles.readableFiles))

              val tryIO: IO[Nothing, Try[Set[FileMetrics]]] =
                analysisResults.redeem(err => IO.point(Failure(err)), results => IO.point(Success(results)))

              tryIO.map(MetricsToolExecutorResult(metricsTool.languageToRun.name, filteredFiles.readableFiles, _))
            case duplicationTool: DuplicationTool =>
              val analysisResults =
                analyser.duplication(duplicationTool, filteredFiles.directory, filteredFiles.readableFiles)

              val tryIO: IO[Nothing, Try[Set[DuplicationClone]]] =
                analysisResults.redeem(err => IO.point(Failure(err)), results => IO.point(Success(results)))

              tryIO.map(
                DuplicationToolExecutorResult(duplicationTool.languageToRun.name, filteredFiles.readableFiles, _))
          }
        }
    }

    postProcess(analysisResult.map(IO.sequence).flatMap(identity))
  }

  private def postProcess(
    analysisResult: IO[CLIError, Seq[ExecutorResult[_]]]): IO[CLIError, Seq[ExecutorResult[_]]] = {
    analysisResult.map { result =>
      val (issuesResults, duplicationResults, metricsResults) =
        result.partitionSubtypes[IssuesToolExecutorResult, DuplicationToolExecutorResult, MetricsToolExecutorResult]

      val reduce = MetricsToolExecutor.reduceMetricsToolResultsByFile _
      val calculateMissingMetrics: Seq[MetricsToolExecutorResult] => Seq[MetricsToolExecutorResult] =
        MetricsToolExecutor.calculateMissingFileMetrics(configuration.projectDirectory, _)

      val processedFileMetrics = reduce.andThen(calculateMissingMetrics)(metricsResults)

      val executorResults = issuesResults ++ duplicationResults ++ processedFileMetrics

      formatter.begin()
      executorResults.foreach(_.analysisResults.foreach(results => formatter.addAll(results.to[List])))
      formatter.end()

      executorResults
    }
  }

  private def issues(tool: Tool,
                     analysisFilesTarget: FilesTarget,
                     configuration: CLIConfiguration.Tool,
                     toolHasConfigFiles: Boolean): IOThrowable[Set[ToolResult]] = {
    for {
      toolConfiguration <- IO.fromTry(getToolConfiguration(tool, toolHasConfigFiles, configuration))
      results <- analyser.analyse(
        tool,
        analysisFilesTarget.directory,
        analysisFilesTarget.readableFiles,
        toolConfiguration,
        configuration.toolTimeout)
    } yield results
  }

  private def getToolConfiguration(tool: Tool,
                                   hasConfigFiles: Boolean,
                                   configuration: CLIConfiguration.Tool): Try[Configuration] = {
    val (baseSubDir, extraValues) = getExtraConfiguration(configuration.extraToolConfigurations, tool)
    (for {
      allToolsConfiguration <- configuration.toolConfigurations
      toolConfiguration <- allToolsConfiguration
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
        Success(CodacyCfg(toolConfiguration.patterns.map(toInternalPattern), baseSubDir, extraValues))
      }
    }).right.getOrElse[Try[Configuration]] {
      logger.info(s"Preparing to run ${tool.name} with defaults")
      Success(FileCfg(baseSubDir, extraValues))
    }
  }

  private def getExtraConfiguration(enginesConfiguration: Option[Map[String, CLIConfiguration.IssuesTool.Extra]],
                                    tool: Tool): (Option[String], Option[Map[String, JsValue]]) = {
    (for {
      engines <- enginesConfiguration
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

  sealed trait ExecutorResult[T <: Result] {
    def analysisResults: Try[Set[T]]
    def analysisResultsAsList: Try[List[Result]] = analysisResults.map(_.to[List])
  }
  final case class IssuesToolExecutorResult(toolName: String, files: Set[Path], analysisResults: Try[Set[ToolResult]])
      extends ExecutorResult[ToolResult]
  final case class MetricsToolExecutorResult(language: String, files: Set[Path], analysisResults: Try[Set[FileMetrics]])
      extends ExecutorResult[FileMetrics]
  final case class DuplicationToolExecutorResult(language: String,
                                                 files: Set[Path],
                                                 analysisResults: Try[Set[DuplicationClone]])
      extends ExecutorResult[DuplicationClone]

  def allTools(toolInput: Option[String],
               configuration: CLIConfiguration.Tool,
               languages: Set[Language]): Either[CLIError, Set[ITool]] = {

    def metricsTools = MetricsToolCollector.fromLanguages(languages)
    def duplicationTools = DuplicationToolCollector.fromLanguages(languages)

    toolInput match {
      case None =>
        val toolsEither = tools(toolInput, configuration, languages)

        toolsEither.map(_ ++ metricsTools ++ duplicationTools)

      case Some("metrics") =>
        Right(metricsTools.map(_.to[ITool]))

      case Some("duplication") =>
        Right(duplicationTools.map(_.to[ITool]))

      case Some(_) =>
        val toolsEither = tools(toolInput, configuration, languages)
        toolsEither.map(_.map(_.to[ITool]))
    }
  }

  def tools(toolInput: Option[String],
            configuration: CLIConfiguration.Tool,
            languages: Set[Language]): Either[CLIError, Set[Tool]] = {

    val toolCollector = new ToolCollector(configuration.allowNetwork)

    def fromRemoteConfig: Either[CLIError, Set[Tool]] = {
      configuration.toolConfigurations.left.map(CLIError.NoRemoteProjectConfiguration).flatMap { toolConfiguration =>
        val toolUuids = toolConfiguration.filter(_.enabled).map(_.uuid)
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
