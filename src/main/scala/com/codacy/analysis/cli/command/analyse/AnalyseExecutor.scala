package com.codacy.analysis.cli.command.analyse

import better.files.File
import com.codacy.analysis.cli.analysis.Analyser
import com.codacy.analysis.cli.clients.api.ProjectConfiguration
import com.codacy.analysis.cli.command.Properties
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor._
import com.codacy.analysis.cli.configuration.CodacyConfigurationFile
import com.codacy.analysis.cli.converters.ConfigurationHelper
import com.codacy.analysis.cli.files.{FileCollector, FilesTarget}
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.cli.model.{CodacyCfg, Configuration, FileCfg, Result}
import com.codacy.analysis.cli.tools.Tool
import com.codacy.analysis.cli.utils.SetOps
import com.codacy.analysis.cli.utils.TryOps._
import org.log4s.{Logger, getLogger}
import play.api.libs.json.JsValue

import scala.util.{Failure, Success, Try}

class AnalyseExecutor(toolInput: Option[String],
                      directory: Option[File],
                      formatter: Formatter,
                      analyser: Analyser[Try],
                      fileCollector: FileCollector[Try],
                      remoteProjectConfiguration: Either[String, ProjectConfiguration],
                      nrParallelTools: Option[Int]) {

  private val logger: Logger = getLogger

  def run(): Either[String, Seq[ExecutorResult]] = {
    formatter.begin()

    val baseDirectory =
      directory.fold(Properties.codacyCode.getOrElse(File.currentWorkingDirectory))(dir =>
        if (dir.isDirectory) dir else dir.parent)

    val localConfigurationFile = CodacyConfigurationFile.search(baseDirectory).flatMap(CodacyConfigurationFile.load)

    val filesTargetAndTool: Either[String, (FilesTarget, Set[Tool])] = for {
      filesTarget <- fileCollector
        .list(baseDirectory, localConfigurationFile, remoteProjectConfiguration)
        .toRight("Could not access project files")
      tools <- tools(toolInput, localConfigurationFile, remoteProjectConfiguration, filesTarget)
    } yield (filesTarget, tools)

    val analysisResult: Either[String, Seq[ExecutorResult]] = filesTargetAndTool.map {
      case (filesTarget, tools) =>
        SetOps.mapInParallel(tools, nrParallelTools) { tool =>
          val analysisResults: Try[Set[Result]] = analyseFiles(tool, filesTarget, localConfigurationFile)
          ExecutorResult(tool.name, analysisResults)
        }
    }

    formatter.end()

    analysisResult
  }

  private def analyseFiles(tool: Tool,
                           filesTarget: FilesTarget,
                           localConfigurationFile: Either[String, CodacyConfigurationFile]): Try[Set[Result]] = {
    for {
      fileTarget <- fileCollector.filter(tool, filesTarget, localConfigurationFile, remoteProjectConfiguration)
      toolHasConfigFiles = fileCollector.hasConfigurationFiles(tool, filesTarget)
      toolConfiguration <- getToolConfiguration(
        tool,
        toolHasConfigFiles,
        localConfigurationFile,
        remoteProjectConfiguration)
      results <- analyser.analyse(tool, fileTarget.directory, fileTarget.files, toolConfiguration)
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
        .toRight[String]("Could not find tool")
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
             |shouldRun:$shouldRun = !needsPatternsToRun:${tool.needsPatternsToRun}|| shouldUseConfigFile:$shouldUseConfigFile|| shouldUseRemoteConfiguredPatterns:$shouldUseRemoteConfiguredPatterns
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

}

object AnalyseExecutor {

  final case class ExecutorResult(toolName: String, analysisResults: Try[Set[Result]])

  def tools(toolInput: Option[String],
            localConfiguration: Either[String, CodacyConfigurationFile],
            remoteProjectConfiguration: Either[String, ProjectConfiguration],
            filesTarget: FilesTarget): Either[String, Set[Tool]] = {

    def fromRemoteConfig: Either[String, Set[Tool]] = {
      remoteProjectConfiguration.flatMap(projectConfiguration =>
        Tool.fromToolUUIDs(projectConfiguration.toolConfiguration.filter(_.isEnabled).map(_.uuid)))
    }

    def fromLocalConfig: Either[String, Set[Tool]] = {
      Tool.fromFileTarget(
        filesTarget,
        localConfiguration.map(_.languageCustomExtensions.mapValues(_.toList).toList).getOrElse(List.empty))
    }

    toolInput.map { toolStr =>
      Tool.fromNameOrUUID(toolStr)
    }.getOrElse {
      for {
        e1 <- fromRemoteConfig.left
        e2 <- fromLocalConfig.left
      } yield s"$e1 and $e2"
    }

  }
}
