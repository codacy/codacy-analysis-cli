package com.codacy.analysis.cli

import java.nio.file.Path

import better.files._
import cats.implicits._
import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.clients.Credentials
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor.{
  DuplicationToolExecutorResult,
  ExecutorResult,
  IssuesToolExecutorResult,
  MetricsToolExecutorResult
}
import com.codacy.analysis.cli.command.{Analyse, CLIApp, Command}
import com.codacy.analysis.cli.configuration.Environment
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.core.analysis.Analyser
import com.codacy.analysis.core.clients.CodacyClient
import com.codacy.analysis.core.clients.api.ProjectConfiguration
import com.codacy.analysis.core.files.FileCollector
import com.codacy.analysis.core.model.{FileMetrics, FileWithMetrics, Metrics, MetricsResult}
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

        val formatter: Formatter = Formatter(analyse.format, analyse.output)
        val analyser: Analyser[Try] = Analyser(analyse.extras.analyser)
        val fileCollector: FileCollector[Try] = FileCollector.defaultCollector()
        val environment = new Environment(sys.env)
        val codacyClientOpt: Option[CodacyClient] = Credentials.get(environment, analyse.api).map(CodacyClient.apply)

        val remoteProjectConfiguration: Either[String, ProjectConfiguration] = codacyClientOpt.fold {
          "No credentials found.".asLeft[ProjectConfiguration]
        } { _.getRemoteConfiguration }

        val analysisResults = new AnalyseExecutor(
          analyse.tool,
          analyse.directory,
          formatter,
          analyser,
          fileCollector,
          remoteProjectConfiguration,
          analyse.parallel,
          analyse.allowNetworkValue,
          analyse.forceFilePermissionsValue,
          analyse.toolTimeout).run()

        val uploadResultFut = uploadResults(codacyClientOpt)(analyse.uploadValue, analyse.commitUuid, analysisResults)

        val uploadResult = if (analyse.uploadValue) {
          Try(Await.result(uploadResultFut, Duration.Inf)) match {
            case Failure(err) =>
              logger.error(err.getMessage)
              Left(err.getMessage)
            case Success(Left(err)) =>
              logger.warn(err)
              Left(err)
            case Success(Right(_)) =>
              logger.info("Completed upload of results to API")
              Right(())
          }
        } else Right(())

        new ExitStatus(analyse.maxAllowedIssues, analyse.failIfIncompleteValue).exitCode(analysisResults, uploadResult)
    }
  }

  private def uploadResults(codacyClientOpt: Option[CodacyClient])(
    upload: Boolean,
    commitUuid: Option[String],
    executorResultsEither: Either[String, Seq[ExecutorResult]]): Future[Either[String, Unit]] = {
    (for {
      uploaderOpt <- ResultsUploader(codacyClientOpt, upload, commitUuid)
      executorResults <- executorResultsEither
    } yield {
      uploaderOpt.map { uploader =>
        val (issuesToolExecutorResult, metricsToolExecutorResult, _) =
          executorResults
            .partitionSubtypes[IssuesToolExecutorResult, MetricsToolExecutorResult, DuplicationToolExecutorResult]

        val toolResultSeq: Seq[ResultsUploader.ToolResults] = toolResults(issuesToolExecutorResult)

        val metricsPerLanguageSeq =
          metricsPerLanguage(metricsToolExecutorResult)

        val metricsResultsSeq: Seq[ResultsUploader.MetricsResults] = metricsResults(metricsPerLanguageSeq)

        uploader.sendResults(toolResultSeq, metricsResultsSeq)
      }.getOrElse(Future.successful(().asRight[String]))
    }).fold(err => Future.successful(err.asLeft[Unit]), identity)
  }

  private def metricsResults(languageAndToolResultSeq: Seq[(String, (Set[Path], Either[Throwable, Set[FileMetrics]]))])
    : Seq[ResultsUploader.MetricsResults] = {
    languageAndToolResultSeq.groupBy {
      case (language, _) => language
    }.map {
      case (language, languageAndFileMetricsSeq) =>
        val results: Set[MetricsResult] = languageAndFileMetricsSeq.map {
          case (_, (files, Right(fileMetrics))) =>
            MetricsResult(Right(fileWithMetrics(files, fileMetrics)))
          case (_, (_, Left(err))) =>
            MetricsResult(Left(err.getMessage))
        }(collection.breakOut)

        ResultsUploader.MetricsResults(language, results)
    }(collection.breakOut)
  }

  private def fileWithMetrics(allFiles: Set[Path], fileMetrics: Set[FileMetrics]): Set[FileWithMetrics] = {
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
      FileWithMetrics(file, metrics)
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

  private def toolResults(issuesToolExecutorResult: Seq[IssuesToolExecutorResult]) = {
    issuesToolExecutorResult.flatMap {
      case IssuesToolExecutorResult(toolName, files, Success(results)) =>
        Option(ResultsUploader.ToolResults(toolName, files, results))
      case IssuesToolExecutorResult(toolName, _, Failure(err)) =>
        logger.warn(s"Skipping upload for $toolName since analysis failed: ${err.getMessage}")
        Option.empty[ResultsUploader.ToolResults]
    }
  }

  private def cleanup(directoryOpt: Option[File]): Unit = {
    val directory = directoryOpt.getOrElse(File.currentWorkingDirectory) / ".codacy.json"
    directory.delete(swallowIOExceptions = true)
  }
}
