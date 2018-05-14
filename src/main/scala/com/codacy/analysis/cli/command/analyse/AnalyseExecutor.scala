package com.codacy.analysis.cli.command.analyse

import java.util.concurrent.ForkJoinPool

import better.files.File
import cats.implicits._
import com.codacy.analysis.cli.analysis.Analyser
import com.codacy.analysis.cli.clients.api.ProjectConfiguration
import com.codacy.analysis.cli.command.Properties
import com.codacy.analysis.cli.configuration.CodacyConfigurationFile
import com.codacy.analysis.cli.converters.ConfigurationHelper
import com.codacy.analysis.cli.files.{FileCollector, FilesTarget}
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.cli.model.{CodacyCfg, Configuration, FileCfg, Result}
import com.codacy.analysis.cli.tools.Tool
import com.codacy.analysis.cli.upload.ResultsUploader
import org.log4s.{Logger, getLogger}
import play.api.libs.json.JsValue

import scala.collection.parallel.ForkJoinTaskSupport
import scala.collection.parallel.immutable.ParSet
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class AnalyseExecutor(toolInput: Option[String],
                      directory: Option[File],
                      formatter: Formatter,
                      analyser: Analyser[Try],
                      uploader: Either[String, ResultsUploader],
                      fileCollector: FileCollector[Try],
                      remoteProjectConfiguration: Either[String, ProjectConfiguration],
                      nrParallelTools: Option[Int])(implicit context: ExecutionContext) {

  private val logger: Logger = getLogger

  def run(): Future[Either[String, Unit]] = {
    formatter.begin()

    val baseDirectory =
      directory.fold(Properties.codacyCode.getOrElse(File.currentWorkingDirectory))(dir =>
        if (dir.isDirectory) dir else dir.parent)

    val localConfigurationFile = CodacyConfigurationFile.search(baseDirectory).flatMap(CodacyConfigurationFile.load)

    val analysisResult: Future[Either[String, Unit]] =
      fileCollector.list(baseDirectory, localConfigurationFile, remoteProjectConfiguration) match {
        case Failure(_) =>
          Future.successful(Left("Could not access project files"))
        case Success(filesTarget) =>
          Tool.fromInput(toolInput, localConfigurationFile, remoteProjectConfiguration, filesTarget) match {
            case Left(error) =>
              Future.successful(Left(error))
            case Right(tools) =>
              analyseAndUpload(tools, filesTarget, localConfigurationFile, nrParallelTools)
          }
      }

    formatter.end()

    analysisResult
  }

  private def analyseAndUpload(tools: Set[Tool],
                               filesTarget: FilesTarget,
                               localConfigurationFile: Either[String, CodacyConfigurationFile],
                               nrParallelTools: Option[Int]): Future[Either[String, Unit]] = {

    val toolsPar: ParSet[Tool] = tools.par

    toolsPar.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(nrParallelTools.getOrElse(2)))

    val uploads: Seq[Future[Either[String, Unit]]] = toolsPar.map { tool =>
      val hasConfigFiles = fileCollector.hasConfigurationFiles(tool, filesTarget)
      analyseAndUpload(tool, hasConfigFiles, filesTarget, localConfigurationFile)
    }(collection.breakOut)

    val joinedUploads: Future[Seq[Either[String, Unit]]] = Future.sequence(uploads)

    joinedUploads.map(sequenceWithFixedLeft("")(_))
  }

  private def analyseAndUpload(
    tool: Tool,
    toolHasConfigFiles: Boolean,
    filesTarget: FilesTarget,
    localConfigurationFile: Either[String, CodacyConfigurationFile]): Future[Either[String, Unit]] = {
    val result: Try[Set[Result]] = for {
      fileTarget <- fileCollector.filter(tool, filesTarget, localConfigurationFile, remoteProjectConfiguration)
      toolConfiguration <- getToolConfiguration(
        tool,
        toolHasConfigFiles,
        localConfigurationFile,
        remoteProjectConfiguration)
      results <- analyser.analyse(tool, fileTarget.directory, fileTarget.files, toolConfiguration)
    } yield results

    result match {
      case Success(res) =>
        logger.info(s"Completed analysis for ${tool.name}")
        res.foreach(formatter.add)
      case Failure(e) =>
        logger.error(e)(s"Failed analysis for ${tool.name}")
    }

    uploader.fold[Future[Either[String, Unit]]]({ message =>
      logger.warn(message)
      Future.successful(().asRight[String])
    }, { upload =>
      for {
        results <- Future.fromTry(result)
        upl <- upload.sendResults(tool.name, results)
      } yield upl
    })
  }

  private def sequenceWithFixedLeft[A](left: A)(eitherIterable: Seq[Either[A, Unit]]): Either[A, Unit] = {
    eitherIterable
      .foldLeft[Either[A, Unit]](Right(())) { (acc, either) =>
        acc.flatMap(_ => either)
      }
      .left
      .map(_ => left)
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
             |shouldUseConfigFile:$shouldUseConfigFile = notEdited:${toolConfiguration.notEdited} && foundToolConfigFile:${hasConfigFiles}
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
}
