package com.codacy.analysis.cli.command

import java.nio.file.Path

import better.files.File
import cats.implicits._
import com.codacy.analysis.cli.CLIError
import com.codacy.analysis.cli.analysis.AnalyseExecutor.{
  DuplicationToolExecutorResult,
  ExecutorResult,
  IssuesToolExecutorResult,
  MetricsToolExecutorResult
}
import com.codacy.analysis.cli.analysis.{AnalyseExecutor, ExitStatus}
import com.codacy.analysis.cli.clients.Credentials
import com.codacy.analysis.cli.configuration.{CLIConfiguration, Environment}
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.cli.toolRepository.ToolRepositoryFactory
import com.codacy.analysis.core.analysis.Analyser
import com.codacy.analysis.core.clients.CodacyClient
import com.codacy.analysis.core.configuration.CodacyConfigurationFileLoader
import com.codacy.analysis.core.files.FileCollector
import com.codacy.analysis.core.git.{Commit, Git, Repository}
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.tools.ToolRepository
import com.codacy.analysis.core.upload.ResultsUploader
import com.codacy.analysis.core.utils.Logger
import com.codacy.analysis.core.utils.SeqOps._
import org.log4s.getLogger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

object AnalyseCommand {

  def apply(analyse: Analyse, env: Map[String, String]): AnalyseCommand = {
    val environment: Environment = new Environment(env)
    val codacyClientOpt: Option[CodacyClient] =
      Credentials.get(environment, analyse.api).map(CodacyClient.apply)
    val configuration: CLIConfiguration =
      CLIConfiguration(codacyClientOpt, environment, analyse, new CodacyConfigurationFileLoader)
    val formatter: Formatter =
      Formatter(
        configuration.analysis.output.format,
        environment.baseProjectDirectory(analyse.directory),
        configuration.analysis.output.file)
    val fileCollector: FileCollector[Try] = FileCollector.defaultCollector()

    val toolRepository: ToolRepository = ToolRepositoryFactory.build(analyse.fetchRemoteTools)

    val analyseExecutor: AnalyseExecutor =
      new AnalyseExecutor(
        formatter,
        Analyser(analyse.extras.analyser),
        fileCollector,
        configuration.analysis,
        toolRepository)
    val uploaderOpt: Either[String, Option[ResultsUploader]] =
      ResultsUploader(codacyClientOpt, configuration.upload.upload, configuration.upload.commitUuid)

    new AnalyseCommand(analyse, configuration, analyseExecutor, uploaderOpt)
  }
}

class AnalyseCommand(analyse: Analyse,
                     configuration: CLIConfiguration,
                     analyseExecutor: AnalyseExecutor,
                     uploaderOpt: Either[String, Option[ResultsUploader]]) {

  Logger.setLevel(analyse.options.verboseValue)

  private val logger: org.log4s.Logger = getLogger

  def run(): ExitStatus.ExitCode = {
    removeCodacyRuntimeConfigurationFiles(configuration.analysis.projectDirectory)

    val analysisAndUpload = for {
      _ <- validate(analyse, configuration)
      analysisResults <- analyseExecutor.run()
      _ <- upload(configuration.upload, analysisResults)
    } yield analysisResults

    removeCodacyRuntimeConfigurationFiles(configuration.analysis.projectDirectory)

    new ExitStatus(configuration.result.maxAllowedIssues, configuration.result.failIfIncomplete)
      .exitCode(analysisAndUpload)
  }

  private def validate(analyse: Analyse, configuration: CLIConfiguration): Either[CLIError, Unit] = {
    Git
      .repository(configuration.analysis.projectDirectory)
      .fold(
        { _ =>
          Right(())
        }, { repository =>
          for {
            _ <- validateNoUncommitedChanges(repository, configuration.upload.upload)
            _ <- validateGitCommitUuid(repository, analyse.commitUuid)
          } yield ()
        })
  }

  private def validateNoUncommitedChanges(repository: Repository, upload: Boolean): Either[CLIError, Unit] = {
    repository.uncommitedFiles.fold(
      { _ =>
        Right(())
      }, { uncommitedFiles =>
        if (uncommitedFiles.nonEmpty) {
          val error: CLIError = CLIError.UncommitedChanges(uncommitedFiles)
          if (upload) {
            logger.error(error.message)
            Left(error)
          } else {
            logger.warn(error.message)
            Right(())
          }
        } else {
          Right(())
        }
      })
  }

  private def validateGitCommitUuid(repository: Repository,
                                    commitUuidOpt: Option[Commit.Uuid]): Either[CLIError, Unit] = {
    (for {
      gitCommit <- repository.latestCommit.toOption
      paramCommitUuid <- commitUuidOpt
      if gitCommit.commitUuid != paramCommitUuid
    } yield {
      val error = CLIError.CommitUuidsDoNotMatch(paramCommitUuid, gitCommit.commitUuid)
      logger.error(error.message)
      Left(error)
    }).getOrElse(Right(()))
  }

  private def upload(configuration: CLIConfiguration.Upload,
                     analysisResults: Seq[AnalyseExecutor.ExecutorResult[_]]): Either[CLIError, Unit] = {
    if (configuration.upload) {
      val uploadResultFut: Future[Either[String, Unit]] =
        uploadResults(analysisResults)

      Try(Await.result(uploadResultFut, Duration.Inf)) match {
        case Failure(err) =>
          logger.error(err.getMessage)
          Left(CLIError.UploadError(err.getMessage))
        case Success(Left(err)) =>
          logger.warn(err)
          Left(CLIError.MissingUploadRequisites(err))
        case Success(Right(_)) =>
          logger.info("Completed upload of results to API")
          Right(())
      }
    } else Right(())
  }

  private def uploadResults(executorResults: Seq[ExecutorResult[_]]): Future[Either[String, Unit]] = {
    uploaderOpt match {
      case Right(Some(uploader)) =>
        val (issuesToolExecutorResult, metricsToolExecutorResult, duplicationToolExecutorResult) =
          executorResults
            .partitionSubtypes[IssuesToolExecutorResult, MetricsToolExecutorResult, DuplicationToolExecutorResult]

        val issuesResultsSeq = issuesToUpload(issuesToolExecutorResult)
        val metricsResultsSeq = metricsToUpload(metricsToolExecutorResult)
        val duplicationResultsSeq = duplicationToUpload(duplicationToolExecutorResult)
        uploader.sendResults(issuesResultsSeq, metricsResultsSeq, duplicationResultsSeq)

      case Right(None) =>
        Future.successful(().asRight[String])

      case Left(err) =>
        Future.successful(err.asLeft[Unit])
    }
  }

  def duplicationToUpload(
    duplicationExecutorToolResults: Seq[DuplicationToolExecutorResult]): Seq[DuplicationResult] = {
    duplicationExecutorToolResults.map {
      case DuplicationToolExecutorResult(language, files, Success(duplicationClones)) =>
        DuplicationResult(language, DuplicationAnalysis.Success(files, duplicationClones))
      case DuplicationToolExecutorResult(language, _, Failure(err)) =>
        DuplicationResult(language, DuplicationAnalysis.Failure(err.getMessage))
    }
  }

  private def metricsToUpload(languageAndToolResultSeq: Seq[MetricsToolExecutorResult]): Seq[MetricsResult] = {
    languageAndToolResultSeq.map {
      case MetricsToolExecutorResult(language, files, Success(fileMetrics)) =>
        MetricsResult(language, MetricsAnalysis.Success(fileWithMetrics(files, fileMetrics)))
      case MetricsToolExecutorResult(language, _, Failure(err)) =>
        MetricsResult(language, MetricsAnalysis.Failure(err.getMessage))
    }(collection.breakOut)
  }

  private def fileWithMetrics(allFiles: Set[Path], fileMetrics: Set[FileMetrics]): Set[MetricsAnalysis.FileResults] = {
    allFiles.map { file =>
      val metrics = fileMetrics.find(_.filename == file).map { metrics =>
        Metrics(
          metrics.complexity,
          metrics.loc,
          metrics.cloc,
          metrics.nrMethods,
          metrics.nrClasses,
          metrics.lineComplexities)
      }
      MetricsAnalysis.FileResults(file, metrics)
    }
  }

  private def issuesToUpload(toolAndIssuesResults: Seq[IssuesToolExecutorResult]): Seq[ResultsUploader.ToolResults] = {
    toolAndIssuesResults.map {
      case IssuesToolExecutorResult(toolName, _, _, files, Success(issues)) =>
        ResultsUploader.ToolResults(toolName, files, Right(issues))
      case IssuesToolExecutorResult(toolName, _, _, files, Failure(error)) =>
        ResultsUploader.ToolResults(toolName, files, Left(error.getMessage))
    }(collection.breakOut)
  }

  //TODO: this can be removed when all tools are using the 3.+ seed version.
  private def removeCodacyRuntimeConfigurationFiles(directory: File): Unit = {
    directory / ".codacy.json" delete (swallowIOExceptions = true)
    directory / ".codacyrc" delete (swallowIOExceptions = true)
    ()
  }
}
