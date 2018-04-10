package com.codacy.analysis.cli.command.analyse

import better.files.File
import cats.implicits._
import com.codacy.analysis.cli.analysis.Analyser
import com.codacy.analysis.cli.clients.api.ProjectConfiguration
import com.codacy.analysis.cli.clients.{APIToken, CodacyClient, Credentials, ProjectToken}
import com.codacy.analysis.cli.command.{Analyse, Properties}
import com.codacy.analysis.cli.configuration.{CodacyConfigurationFile, Environment}
import com.codacy.analysis.cli.converters.ConfigurationHelper
import com.codacy.analysis.cli.files.FileCollector
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.cli.model.{CodacyCfg, Configuration, FileCfg}
import com.codacy.analysis.cli.tools.Tool
import com.codacy.analysis.cli.utils
import org.log4s.{Logger, getLogger}
import play.api.libs.json.JsValue

import scala.util.{Failure, Success, Try}

class AnalyseExecutor(analise: Analyse,
                      formatter: Formatter,
                      analyser: Analyser[Try],
                      fileCollector: FileCollector[Try],
                      environment: Environment)
    extends Runnable {

  utils.Logger.setLevel(analise.options.verbose.## > 0)

  private val logger: Logger = getLogger

  def run(): Unit = {
    formatter.begin()

    val baseDirectory =
      analise.directory.fold(Properties.codacyCode.getOrElse(File.currentWorkingDirectory))(dir =>
        if (dir.isDirectory) dir else dir.parent)

    val credentials = Credentials.getCredentials(environment, analise.api)
    val remoteConfiguration: Either[String, ProjectConfiguration] = getRemoteConfiguration(credentials)
    val localConfigurationFile = CodacyConfigurationFile.load(baseDirectory)

    val result = for {
      tool <- Tool.from(analise.tool)
      fileTargets <- fileCollector.list(tool, baseDirectory, localConfigurationFile, remoteConfiguration)
      fileTarget <- fileCollector.filter(tool, fileTargets, localConfigurationFile, remoteConfiguration)
      toolConfiguration = getToolConfiguration(
        tool,
        fileTarget.configFiles,
        localConfigurationFile,
        remoteConfiguration)
      results <- analyser.analyse(tool, fileTarget.directory, fileTarget.files, toolConfiguration)
    } yield results

    result match {
      case Success(res) =>
        logger.info(s"Completed analysis for ${analise.tool}")
        res.foreach(formatter.add)
      case Failure(e) =>
        logger.error(e)(s"Failed analysis for ${analise.tool}")
    }

    formatter.end()
  }

  private def getRemoteConfiguration(credentials: Option[Credentials]): Either[String, ProjectConfiguration] = {

    val apiClient = credentials.fold[Either[String, CodacyClient]]("Credentials not found".asLeft)(creds =>
      CodacyClient.apply(creds).asRight)

    credentials.toRight("Credentials not found").flatMap {
      case _: APIToken =>
        for {
          client <- apiClient
          userName <- analise.api.username.toRight("Username not found")
          projectName <- analise.api.project.toRight("Project name not found")
          config <- client.getProjectConfiguration(userName, projectName)
        } yield config

      case _: ProjectToken =>
        for {
          client <- apiClient
          config <- client.getProjectConfiguration
        } yield config
    }

  }

  private def getToolConfiguration(tool: Tool,
                                   configFiles: Set[File],
                                   localConfiguration: Either[String, CodacyConfigurationFile],
                                   remoteConfiguration: Either[String, ProjectConfiguration]): Configuration = {
    val (baseSubDir, extraValues) = getExtraConfiguration(localConfiguration, tool)
    (for {
      projectConfig <- remoteConfiguration
      toolConfiguration <- projectConfig.toolConfiguration
        .find(_.uuid.equalsIgnoreCase(tool.uuid))
        .toRight("Could not find tool")
    } yield {
      val shouldUseConfigFile = toolConfiguration.notEdited && configFiles.nonEmpty
      if (shouldUseConfigFile) {
        logger.info(s"Preparing to run ${tool.name} with remote configuration")
        CodacyCfg(
          toolConfiguration.patterns.map(ConfigurationHelper.apiPatternToInternalPattern),
          baseSubDir,
          extraValues)
      } else {
        logger.info(s"Preparing to run ${tool.name} with configuration file")
        FileCfg(baseSubDir, extraValues)
      }
    }).right.getOrElse {
      logger.info(s"Preparing to run ${analise.tool} with defaults")
      FileCfg(baseSubDir, extraValues)
    }
  }

  private def getExtraConfiguration(localConfiguration: Either[String, CodacyConfigurationFile],
                                    tool: Tool): (Option[String], Option[Map[String, JsValue]]) = {
    (for {
      config <- localConfiguration.toOption
      engines <- config.engines
      engineConfig <- engines.get(tool.name)
    } yield engineConfig).fold {
      logger.info(s"Could not find local extra configuration for ${analise.tool}")
      (Option.empty[String], Option.empty[Map[String, JsValue]])
    } { ec =>
      logger.info(s"Found local extra configuration for ${analise.tool}")
      (ec.baseSubDir, ec.extraValues)
    }
  }
}
