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
import com.codacy.analysis.core.utils.SeqOps._
import com.codacy.analysis.core.utils.TryOps._
import com.codacy.analysis.core.utils.{LanguagesHelper, SetOps}
import com.codacy.plugins.api.PatternDescription
import org.log4s.{Logger, getLogger}
import play.api.libs.json.JsValue

import scala.sys.process.Process
import scala.util.{Failure, Success, Try}

class AnalyseExecutor(formatter: Formatter,
                      analyser: Analyser,
                      fileCollector: FileCollector[Try],
                      configuration: CLIConfiguration.Analysis,
                      toolSelector: ToolSelector) {

  import com.codacy.analysis.cli.analysis.AnalyseExecutor._

  private val logger: Logger = getLogger

  def run(): Either[CLIError, Seq[ExecutorResult[_]]] = {

    if (configuration.forceFilePermissions) {
      overrideFilePermissions(configuration.projectDirectory)
    }

    val filesTargetAndTool: Either[CLIError, (FilesTarget, FilesTarget, Set[ITool])] = for {
      allFilesTarget <- fileCollector.list(configuration.projectDirectory).toRight(CLIError.FilesAccessError)
      filesGlobalTarget = fileCollector.filterGlobal(allFilesTarget, configuration.fileExclusionRules)
      languages =
        LanguagesHelper.fromFileTarget(filesGlobalTarget, configuration.fileExclusionRules.allowedExtensionsByLanguage)
      tools <- toolSelector.allTools(
        configuration.tool,
        configuration.toolConfiguration,
        languages,
        configuration.registryAddress)
    } yield (allFilesTarget, filesGlobalTarget, tools)

    val analysisResult: Either[CLIError, Seq[ExecutorResult[_]]] = filesTargetAndTool.map {
      case (allFiles, globalFiles, tools) =>
        SetOps.mapInParallel[ITool, ExecutorResult[_]](tools, configuration.parallel) { tool: ITool =>
          val filteredFiles: FilesTarget =
            fileCollector.filterTool(tool, globalFiles, configuration.fileExclusionRules)

          tool match {
            case tool: Tool =>
              val fullToolSpec: Option[FullToolSpec] = toolSelector.fromUuid(tool.uuid).toOption
              val toolHasConfigFiles = fileCollector.hasConfigurationFiles(tool, allFiles)

              val analysisResults =
                issues(
                  tool,
                  filteredFiles,
                  configuration.toolConfiguration,
                  toolHasConfigFiles,
                  configuration.tmpDirectory,
                  maxToolMemory = configuration.maxToolMemory)

              IssuesToolExecutorResult(
                tool.name,
                fullToolSpec.map(_.toolApiSpec),
                fullToolSpec.map(_.patternDescriptions).getOrElse(Set.empty[PatternDescription]),
                fullToolSpec.map(_.tool.prefix),
                filteredFiles.readableFiles,
                analysisResults)

            case metricsTool: MetricsTool =>
              val analysisResults =
                analyser.metrics(
                  metricsTool,
                  filteredFiles.directory,
                  filteredFiles.readableFiles,
                  configuration.tmpDirectory,
                  maxToolMemory = configuration.maxToolMemory)

              MetricsToolExecutorResult(metricsTool.languageToRun.name, filteredFiles.readableFiles, analysisResults)

            case duplicationTool: DuplicationTool =>
              val analysisResults =
                analyser.duplication(
                  duplicationTool,
                  filteredFiles.directory,
                  filteredFiles.readableFiles,
                  configuration.tmpDirectory,
                  maxToolMemory = configuration.maxToolMemory)

              DuplicationToolExecutorResult(
                duplicationTool.languageToRun.name,
                filteredFiles.readableFiles,
                analysisResults)
          }
        }
    }

    postProcess(analysisResult)
  }

  private def postProcess(
    analysisResult: Either[CLIError, Seq[ExecutorResult[_]]]): Either[CLIError, Seq[ExecutorResult[_]]] = {
    analysisResult.map { result =>
      val (issuesResults, duplicationResults, metricsResults) =
        result.partitionSubtypes[IssuesToolExecutorResult, DuplicationToolExecutorResult, MetricsToolExecutorResult]

      val reduce = MetricsToolExecutor.reduceMetricsToolResultsByFile _
      val calculateMissingMetrics: Seq[MetricsToolExecutorResult] => Seq[MetricsToolExecutorResult] =
        MetricsToolExecutor.calculateMissingFileMetrics(configuration.projectDirectory, _)

      val processedFileMetrics = reduce.andThen(calculateMissingMetrics)(metricsResults)

      val executorResults = issuesResults ++ duplicationResults ++ processedFileMetrics

      formatter.begin()
      executorResults.foreach {
        case toolResults: IssuesToolExecutorResult =>
          toolResults.analysisResults.foreach(
            results =>
              formatter.addAll(
                toolResults.toolSpecification,
                toolResults.patternDescriptions,
                toolResults.prefix,
                results.to[List]))
        case toolResults =>
          toolResults.analysisResults.foreach(results => formatter.addAll(None, Set.empty, None, results.to[List]))
      }
      formatter.end()

      executorResults
    }
  }

  private def issues(tool: Tool,
                     analysisFilesTarget: FilesTarget,
                     configuration: CLIConfiguration.Tool,
                     toolHasConfigFiles: Boolean,
                     tmpDirectory: Option[File],
                     maxToolMemory: Option[String]): Try[Set[ToolResult]] = {
    for {
      toolConfiguration <- getToolConfiguration(tool, toolHasConfigFiles, configuration)
      results <- analyser.analyse(
        tool,
        analysisFilesTarget.directory,
        analysisFilesTarget.readableFiles,
        toolConfiguration,
        tmpDirectory,
        configuration.toolTimeout,
        maxToolMemory)
    } yield results
  }

  private def getToolConfiguration(tool: Tool,
                                   hasConfigFiles: Boolean,
                                   configuration: CLIConfiguration.Tool): Try[Configuration] = {
    val (baseSubDir, extraValues) =
      getExtraConfiguration(configuration.extraToolConfigurations, tool)
    (for {
      allToolsConfiguration <- configuration.toolConfigurations
      toolConfiguration <-
        allToolsConfiguration
          .find(_.uuid.equalsIgnoreCase(tool.uuid))
          .toRight[String](s"Could not find configuration for tool ${tool.name}")
    } yield {
      val shouldUseConfigFile = toolConfiguration.notEdited && hasConfigFiles
      val shouldUseRemoteConfiguredPatterns = !shouldUseConfigFile && toolConfiguration.patterns.nonEmpty
      // TODO: Review isEnabled condition when running multiple tools since we might want to force this for single tools
      // val shouldRun = toolConfiguration.isEnabled && (shouldUseConfigFile || shouldUseRemoteConfiguredPatterns)
      val shouldRun = shouldUseConfigFile || shouldUseRemoteConfiguredPatterns

      if (!shouldRun) {
        logger.error(s"""Could not find conditions to run tool ${tool.name} with:
             |shouldUseConfigFile:$shouldUseConfigFile = notEdited:${toolConfiguration.notEdited} && foundToolConfigFile:$hasConfigFiles
             |shouldUseRemoteConfiguredPatterns:$shouldUseRemoteConfiguredPatterns = !shouldUseConfigFile:$shouldUseConfigFile && hasPatterns:${toolConfiguration.patterns.nonEmpty}
             |shouldRun:$shouldRun = shouldUseConfigFile:$shouldUseConfigFile || shouldUseRemoteConfiguredPatterns:$shouldUseRemoteConfiguredPatterns
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
  }

  final case class IssuesToolExecutorResult(
    toolName: String,
    toolSpecification: Option[com.codacy.plugins.api.results.Tool.Specification],
    patternDescriptions: Set[PatternDescription],
    prefix: Option[String],
    files: Set[Path],
    analysisResults: Try[Set[ToolResult]])
      extends ExecutorResult[ToolResult]

  final case class MetricsToolExecutorResult(language: String, files: Set[Path], analysisResults: Try[Set[FileMetrics]])
      extends ExecutorResult[FileMetrics]

  final case class DuplicationToolExecutorResult(language: String,
                                                 files: Set[Path],
                                                 analysisResults: Try[Set[DuplicationClone]])
      extends ExecutorResult[DuplicationClone]

}
