package com.codacy.analysis.cli

import java.nio.file.Path

import better.files._
import cats.implicits._
import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.clients.Credentials
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor._
import com.codacy.analysis.cli.command.{Analyse, CLIApp, Command}
import com.codacy.analysis.cli.configuration.{CLIConfiguration, Environment}
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.core.analysis.Analyser
import com.codacy.analysis.core.clients.CodacyClient
import com.codacy.analysis.core.configuration.CodacyConfigurationFile
import com.codacy.analysis.core.files.FileCollector
import com.codacy.analysis.core.git.{Commit, Git}
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.upload.ResultsUploader
import com.codacy.analysis.core.utils.Logger
import com.codacy.analysis.core.utils.SeqOps._
import org.log4s.getLogger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

object Main extends MainImpl

class MainImpl extends CLIApp {

  private val logger: org.log4s.Logger = getLogger

  override def run(command: Command): Unit = {
    exit(runCommand(command))
  }

  def runCommand(command: Command): Int = {
    command match {
      case analyse: Analyse =>
        Logger.setLevel(analyse.options.verboseValue)

        val environment = new Environment(sys.env)
        val codacyClientOpt: Option[CodacyClient] = Credentials.get(environment, analyse.api).map(CodacyClient.apply)

        val configuration: CLIConfiguration =
          CLIConfiguration(codacyClientOpt, environment, analyse, new CodacyConfigurationFile.Loader)

        cleanup(configuration.analysis.projectDirectory)

        val analysisAndUpload = for {
          _ <- validate(analyse, configuration)
          analysisResults <- analysis(Analyser(analyse.extras.analyser), configuration.analysis)
          _ <- upload(configuration.upload, codacyClientOpt, analysisResults)
        } yield {
          analysisResults
        }

        cleanup(configuration.analysis.projectDirectory)
        new ExitStatus(configuration.result.maxAllowedIssues, configuration.result.failIfIncomplete)
          .exitCode(analysisAndUpload)
    }
  }

  private def validate(analyse: Analyse, configuration: CLIConfiguration): Either[CLIError, Unit] = {
    for {
      _ <- validateNoUncommitedChanges(configuration.analysis.projectDirectory, configuration.upload.upload)
      _ <- validateCommitUuid(configuration.analysis.projectDirectory, analyse.commitUuid)
    } yield ()
  }

  private def validateNoUncommitedChanges(projectDirectory: File, upload: Boolean): Either[CLIError, Unit] = {
    (for {
      repo <- Git.repository(projectDirectory)
      uncommitedFiles <- repo.uncommitedFiles
    } yield {
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
    }).getOrElse(Right(()))
  }

  private def validateCommitUuid(projectDirectory: File, commitUuidOpt: Option[Commit.Uuid]): Either[CLIError, Unit] = {
    (for {
      repo <- Git.repository(projectDirectory).toOption
      gitCommit <- repo.latestCommit.toOption
      paramCommitUuid <- commitUuidOpt
      if gitCommit.commitUuid != paramCommitUuid
    } yield {
      val error = CLIError.CommitUuidsDoNotMatch(paramCommitUuid, gitCommit.commitUuid)
      logger.error(error.message)
      Left(error)
    }).getOrElse(Right(()))
  }

  private def analysis(analyser: Analyser[Try],
                       configuration: CLIConfiguration.Analysis): Either[CLIError, Seq[ExecutorResult[_]]] = {
    val formatter: Formatter = Formatter(configuration.output.format, configuration.output.file)
    val fileCollector: FileCollector[Try] = FileCollector.defaultCollector()

    new AnalyseExecutor(formatter, analyser, fileCollector, configuration).run()
  }

  private def upload(configuration: CLIConfiguration.Upload,
                     codacyClientOpt: Option[CodacyClient],
                     analysisResults: Seq[AnalyseExecutor.ExecutorResult[_]]): Either[CLIError, Unit] = {

    val uploadResultFut: Future[Either[String, Unit]] =
      uploadResults(codacyClientOpt)(configuration.upload, configuration.commitUuid, analysisResults)

    if (configuration.upload) {
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

  private def uploadResults(codacyClientOpt: Option[CodacyClient])(
    upload: Boolean,
    commitUuid: Option[Commit.Uuid],
    executorResults: Seq[ExecutorResult[_]]): Future[Either[String, Unit]] = {
    (for {
      uploaderOpt <- ResultsUploader(codacyClientOpt, upload, commitUuid)
    } yield {
      uploaderOpt.map { uploader =>
        val (issuesToolExecutorResult, metricsToolExecutorResult, duplicationToolExecutorResult) =
          executorResults
            .partitionSubtypes[IssuesToolExecutorResult, MetricsToolExecutorResult, DuplicationToolExecutorResult]

        val issuesPerToolSeq = issuesPerTool(issuesToolExecutorResult)
        val issuesResultsSeq = issuesResults(issuesPerToolSeq)

        val metricsPerLanguageSeq = metricsPerLanguage(metricsToolExecutorResult)
        val metricsResultsSeq = metricsResults(metricsPerLanguageSeq)

        val duplicationResultsSeq = duplicationResults(duplicationToolExecutorResult)

        uploader.sendResults(issuesResultsSeq, metricsResultsSeq, duplicationResultsSeq)
      }.getOrElse(Future.successful(().asRight[String]))
    }).fold(err => Future.successful(err.asLeft[Unit]), identity)
  }

  def duplicationResults(duplicationExecutorToolResults: Seq[DuplicationToolExecutorResult]): Seq[DuplicationResult] = {
    duplicationExecutorToolResults.map {
      case DuplicationToolExecutorResult(language, files, Success(duplicationClones)) =>
        DuplicationResult(language, DuplicationAnalysis.Success(files, duplicationClones))
      case DuplicationToolExecutorResult(language, _, Failure(err)) =>
        DuplicationResult(language, DuplicationAnalysis.Failure(err.getMessage))
    }
  }

  private def metricsResults(
    languageAndToolResultSeq: Seq[(String, (Set[Path], Either[Throwable, Set[FileMetrics]]))]): Seq[MetricsResult] = {
    languageAndToolResultSeq.groupBy {
      case (language, _) => language
    }.flatMap {
      case (language, languageAndFileMetricsSeq) =>
        languageAndFileMetricsSeq.map {
          case (_, (files, Right(fileMetrics))) =>
            MetricsResult(language, MetricsAnalysis.Success(fileWithMetrics(files, fileMetrics)))
          case (_, (_, Left(err))) =>
            MetricsResult(language, MetricsAnalysis.Failure(err.getMessage))
        }(collection.breakOut)
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

  private def metricsPerLanguage(metricsToolExecutorResult: Seq[MetricsToolExecutorResult])
    : Seq[(String, (Set[Path], Either[Throwable, Set[FileMetrics]]))] = {
    metricsToolExecutorResult.flatMap {
      case MetricsToolExecutorResult(language, files, Success(fileMetrics)) =>
        Option((language, (files, Right(fileMetrics))))
      case MetricsToolExecutorResult(language, files, Failure(err)) =>
        Option((language, (files, Left(err))))
    }
  }

  private def issuesResults(toolAndIssuesResults: Seq[(String, (Set[Path], Either[Throwable, Set[ToolResult]]))])
    : Seq[ResultsUploader.ToolResults] = {
    toolAndIssuesResults.groupBy {
      case (toolName, _) => toolName
    }.flatMap {
      case (toolName, toolAndIssuesSeq) =>
        toolAndIssuesSeq.map {
          case (_, (files, Right(issues))) =>
            ResultsUploader.ToolResults(toolName, files, Right(issues))
          case (_, (files, Left(err))) =>
            ResultsUploader.ToolResults(toolName, files, Left(err.getMessage))
        }(collection.breakOut)

    }(collection.breakOut)
  }

  private def issuesPerTool(issuesToolExecutorResult: Seq[IssuesToolExecutorResult])
    : Seq[(String, (Set[Path], Either[Throwable, Set[ToolResult]]))] = {
    issuesToolExecutorResult.flatMap {
      case IssuesToolExecutorResult(tool, files, Success(results)) =>
        Option((tool, (files, Right(results))))
      case IssuesToolExecutorResult(tool, files, Failure(err)) =>
        Option((tool, (files, Left(err))))
    }
  }

  private def cleanup(directory: File): Unit = {
    directory / ".codacy.json" delete (swallowIOExceptions = true)
    directory / ".codacyrc" delete (swallowIOExceptions = true)
    ()
  }
}
