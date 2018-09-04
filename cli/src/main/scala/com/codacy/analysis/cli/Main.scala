package com.codacy.analysis.cli

import java.nio.file.Path

import better.files._
import cats.implicits._
import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.clients.Credentials
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor._
import com.codacy.analysis.cli.command.{Analyse, CLIApp, Command}
import com.codacy.analysis.cli.configuration.{Environment, CLIProperties}
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.core.analysis.Analyser
import com.codacy.analysis.core.clients.CodacyClient
import com.codacy.analysis.core.clients.api.ProjectConfiguration
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
        cleanup(analyse.directory)
        Logger.setLevel(analyse.options.verboseValue)

        val environment = new Environment(sys.env)
        val codacyClientOpt: Option[CodacyClient] = Credentials.get(environment, analyse.api).map(CodacyClient.apply)
        val properties: CLIProperties = CLIProperties(codacyClientOpt, environment, analyse)

        //TODO(31/08/2018): In the next tickets:
        // (2) validate commit cli parameter with commit retrieved from jGit
        val analysisAndUpload = for {
          _ <- validate(properties)
          analysisResults <- analysis(analyse, properties.analysis.projectDirectory, codacyClientOpt)
          _ <- upload(analyse, properties.upload.commitUuid, codacyClientOpt, analysisResults)
        } yield {
          analysisResults
        }

        new ExitStatus(properties.result.maxAllowedIssues, properties.result.failIfIncomplete)
          .exitCode(analysisAndUpload)
    }
  }

  private def validate(properties: CLIProperties): Either[CLIError, Unit] = {
    (for {
      repo <- Git.repository(properties.analysis.projectDirectory)
      uncommitedFiles <- repo.uncommitedFiles
    } yield {
      if (uncommitedFiles.nonEmpty) {
        val error: CLIError = CLIError.UncommitedChanges(uncommitedFiles)
        if (properties.upload.upload) {
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

  private def analysis(analyse: Analyse,
                       projectDirectory: File,
                       codacyClientOpt: Option[CodacyClient]): Either[CLIError, Seq[ExecutorResult[_]]] = {
    val formatter: Formatter = Formatter(analyse.format, analyse.output)
    val analyser: Analyser[Try] = Analyser(analyse.extras.analyser)
    val fileCollector: FileCollector[Try] = FileCollector.defaultCollector()

    val remoteProjectConfiguration: Either[String, ProjectConfiguration] = codacyClientOpt.fold {
      "No credentials found.".asLeft[ProjectConfiguration]
    } {
      _.getRemoteConfiguration
    }

    new AnalyseExecutor(
      analyse.tool,
      projectDirectory,
      formatter,
      analyser,
      fileCollector,
      remoteProjectConfiguration,
      analyse.parallel,
      analyse.allowNetworkValue,
      analyse.forceFilePermissionsValue,
      analyse.toolTimeout).run()
  }

  private def upload(analyse: Analyse,
                     commitUuid: Option[Commit.Uuid],
                     codacyClientOpt: Option[CodacyClient],
                     analysisResults: Seq[AnalyseExecutor.ExecutorResult[_]]): Either[CLIError, Unit] = {

    val uploadResultFut: Future[Either[String, Unit]] =
      uploadResults(codacyClientOpt)(analyse.uploadValue, commitUuid, analysisResults)

    if (analyse.uploadValue) {
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

  private def cleanup(directoryOpt: Option[File]): Unit = {
    val directory = directoryOpt.getOrElse(File.currentWorkingDirectory) / ".codacy.json"
    directory.delete(swallowIOExceptions = true)
  }
}
