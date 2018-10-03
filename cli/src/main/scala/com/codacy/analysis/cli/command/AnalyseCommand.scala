package com.codacy.analysis.cli.command

import java.nio.file.Path

import better.files.File
import cats.implicits._
import com.codacy.analysis.cli.CLIError
import com.codacy.analysis.cli.analysis.AnalyseExecutor.{DuplicationToolExecutorResult, ExecutorResult, IssuesToolExecutorResult, MetricsToolExecutorResult}
import com.codacy.analysis.cli.analysis.{AnalyseExecutor, ExitStatus}
import com.codacy.analysis.cli.clients.Credentials
import com.codacy.analysis.cli.configuration.{CLIConfiguration, Environment}
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.core.analysis.Analyser
import com.codacy.analysis.core.clients.CodacyClient
import com.codacy.analysis.core.configuration.CodacyConfigurationFileLoader
import com.codacy.analysis.core.files.FileCollector
import com.codacy.analysis.core.git.{Commit, Git, Repository}
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.upload.ResultsUploader
import com.codacy.analysis.core.utils.IOHelper.IOThrowable
import com.codacy.analysis.core.utils.Logger
import com.codacy.analysis.core.utils.SeqOps._
import org.log4s.getLogger
import scalaz.zio.IO

import scala.util.{Failure, Success}

object AnalyseCommand {

  def apply(analyse: Analyse, env: Map[String, String]): IO[Nothing, AnalyseCommand] = {
    val environment: Environment = new Environment(env)
    val codacyClientOpt: Option[CodacyClient] = Credentials.get(environment, analyse.api).map(CodacyClient.apply)
    val configurationIO: IO[Nothing, CLIConfiguration] =
      CLIConfiguration(codacyClientOpt, environment, analyse, new CodacyConfigurationFileLoader)

    val fileCollector: FileCollector[IOThrowable] = FileCollector.defaultCollector()

    configurationIO.map { configuration =>
      val formatter: Formatter = Formatter(configuration.analysis.output.format, configuration.analysis.output.file)
      val analyseExecutor: AnalyseExecutor =
        new AnalyseExecutor(formatter, Analyser(analyse.extras.analyser), fileCollector, configuration.analysis)

      val uploaderOpt: Either[String, Option[ResultsUploader]] =
        ResultsUploader(codacyClientOpt, configuration.upload.upload, configuration.upload.commitUuid)

      new AnalyseCommand(analyse, configuration, analyseExecutor, uploaderOpt)
    }
  }
}

class AnalyseCommand(analyse: Analyse,
                     configuration: CLIConfiguration,
                     analyseExecutor: AnalyseExecutor,
                     uploaderOpt: Either[String, Option[ResultsUploader]]) {

  Logger.setLevel(analyse.options.verboseValue)

  private val logger: org.log4s.Logger = getLogger

  def run(): IO[Nothing, ExitStatus.ExitCode] = {
    removeCodacyRuntimeConfigurationFiles(configuration.analysis.projectDirectory)

    val analysisAndUpload = for {
      _ <- validate(analyse, configuration)
      analysisResults <- analyseExecutor.run()
      _ <- upload(configuration.upload, analysisResults)
    } yield analysisResults

    removeCodacyRuntimeConfigurationFiles(configuration.analysis.projectDirectory)

    val exitStatus =
      new ExitStatus(configuration.result.maxAllowedIssues, configuration.result.failIfIncomplete)

    analysisAndUpload.redeem({ error =>
      IO.point(exitStatus.exitCode(Left(error)))
    }, { succ =>
      IO.point(exitStatus.exitCode(Right(succ)))
    })

  }

  private def validate(analyse: Analyse, configuration: CLIConfiguration): IO[CLIError, Unit] = {
    Git
      .repository(configuration.analysis.projectDirectory)
      .redeem(
        { _ =>
          IO.now(())
        }, { repository =>
          for {
            _ <- validateNoUncommitedChanges(repository, configuration.upload.upload)
            _ <- validateGitCommitUuid(repository, analyse.commitUuid)
          } yield ()
        })
  }

  private def validateNoUncommitedChanges(repository: Repository, upload: Boolean): IO[CLIError, Unit] = {
    repository.uncommitedFiles.redeem(
      { _ =>
        IO.now(())
      }, { uncommitedFiles =>
        if (uncommitedFiles.nonEmpty) {
          val error: CLIError = CLIError.UncommitedChanges(uncommitedFiles)
          if (upload) {
            logger.error(error.message)
            IO.fail(error)
          } else {
            logger.warn(error.message)
            IO.now(())
          }
        } else {
          IO.now(())
        }
      })
  }

  private def validateGitCommitUuid(repository: Repository, commitUuidOpt: Option[Commit.Uuid]): IO[CLIError, Unit] = {
    commitUuidOpt match {
      case None =>
        IO.now(())
      case Some(paramCommitUuid) =>
        repository.latestCommit.redeem(_ => IO.now(()), { gitCommit =>
          if (gitCommit.commitUuid != paramCommitUuid) {
            val error = CLIError.CommitUuidsDoNotMatch(paramCommitUuid, gitCommit.commitUuid)
            logger.error(error.message)
            IO.fail(error)
          } else IO.now(())
        })
    }
  }

  private def upload(configuration: CLIConfiguration.Upload,
                     analysisResults: Seq[AnalyseExecutor.ExecutorResult[_]]): IO[CLIError, Unit] = {
    if (configuration.upload) {
      val uploadResultFut: IO[Nothing, Either[String, Unit]] =
        uploadResults(analysisResults)

      uploadResultFut.flatMap {
        case Left(err) =>
          logger.warn(err)
          IO.fail(CLIError.MissingUploadRequisites(err))
        case Right(_) =>
          logger.info("Completed upload of results to API")
          IO.point(())
      }
    } else IO.now(())
  }

  private def uploadResults(executorResults: Seq[ExecutorResult[_]]): IO[Nothing, Either[String, Unit]] = {
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
        IO.now(().asRight[String])

      case Left(err) =>
        IO.now(err.asLeft[Unit])
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
      case IssuesToolExecutorResult(toolName, files, Success(issues)) =>
        ResultsUploader.ToolResults(toolName, files, Right(issues))
      case IssuesToolExecutorResult(toolName, files, Failure(error)) =>
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
