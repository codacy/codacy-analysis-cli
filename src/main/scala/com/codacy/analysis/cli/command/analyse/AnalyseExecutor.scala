package com.codacy.analysis.cli.command.analyse

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.cli.analysis.Analyser
import com.codacy.analysis.cli.clients.api.ProjectConfiguration
import com.codacy.analysis.cli.command.{Analyse, Properties}
import com.codacy.analysis.cli.configuration.CodacyConfigurationFile
import com.codacy.analysis.cli.converters.ConfigurationHelper
import com.codacy.analysis.cli.files.FileCollector
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.cli.model.{CodacyCfg, Configuration, FileCfg}
import com.codacy.analysis.cli.tools.Tool
import org.log4s.{Logger, getLogger}
import play.api.libs.json.JsValue

import scala.util.{Failure, Success, Try}

class AnalyseExecutor(analyse: Analyse,
                      formatter: Formatter,
                      analyser: Analyser[Try],
                      fileCollector: FileCollector[Try],
                      remoteProjectConfiguration: Either[String, ProjectConfiguration])
    extends Runnable {

  private val logger: Logger = getLogger

  def run(): Unit = {
    formatter.begin()

    val baseDirectory =
      analyse.directory.fold(Properties.codacyCode.getOrElse(File.currentWorkingDirectory))(dir =>
        if (dir.isDirectory) dir else dir.parent)

    val localConfigurationFile = CodacyConfigurationFile.search(baseDirectory).flatMap(CodacyConfigurationFile.load)

    val result = for {
      tool <- Tool.from(analyse.tool)
      fileTargets <- fileCollector.list(tool, baseDirectory, localConfigurationFile, remoteProjectConfiguration)
      fileTarget <- fileCollector.filter(tool, fileTargets, localConfigurationFile, remoteProjectConfiguration)
      toolConfiguration <- getToolConfiguration(
        tool,
        fileTarget.configFiles,
        localConfigurationFile,
        remoteProjectConfiguration)
      results <- analyser.analyse(tool, fileTarget.directory, fileTarget.files, toolConfiguration)
    } yield results

    result match {
      case Success(res) =>
        logger.info(s"Completed analysis for ${analyse.tool}")
        res.foreach(formatter.add)
      case Failure(e) =>
        logger.error(e)(s"Failed analysis for ${analyse.tool}")
    }

    formatter.end()
  }

  private def getToolConfiguration(tool: Tool,
                                   configFiles: Set[Path],
                                   localConfiguration: Either[String, CodacyConfigurationFile],
                                   remoteConfiguration: Either[String, ProjectConfiguration]): Try[Configuration] = {
    val (baseSubDir, extraValues) = getExtraConfiguration(localConfiguration, tool)
    (for {
      projectConfig <- remoteConfiguration
      toolConfiguration <- projectConfig.toolConfiguration
        .find(_.uuid.equalsIgnoreCase(tool.uuid))
        .toRight[String]("Could not find tool")
    } yield {
      val shouldUseConfigFile = toolConfiguration.notEdited && configFiles.nonEmpty
      val shouldUseRemoteConfiguredPatterns = !shouldUseConfigFile && tool.allowsUIConfiguration && toolConfiguration.patterns.nonEmpty
      // TODO: Review isEnabled condition when running multiple tools since we might want to force this for single tools
      // val shouldRun = toolConfiguration.isEnabled && (!tool.needsPatternsToRun || shouldUseConfigFile || shouldUseRemoteConfiguredPatterns)
      val shouldRun = !tool.needsPatternsToRun || shouldUseConfigFile || shouldUseRemoteConfiguredPatterns

      if (!shouldRun) {
        logger.error(s"""Could not find conditions to run tool ${tool.name} with:
             |shouldUseConfigFile:$shouldUseConfigFile = notEdited:${toolConfiguration.notEdited} && foundToolConfigFile:${configFiles.nonEmpty}
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
      logger.info(s"Preparing to run ${analyse.tool} with defaults")
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
      logger.info(s"Could not find local extra configuration for ${analyse.tool}")
      (Option.empty[String], Option.empty[Map[String, JsValue]])
    } { ec =>
      logger.info(s"Found local extra configuration for ${analyse.tool}")
      (ec.baseSubDir, ec.extraValues)
    }
  }
}
