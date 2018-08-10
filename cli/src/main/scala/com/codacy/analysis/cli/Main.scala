package com.codacy.analysis.cli

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
import com.codacy.analysis.core.model.{FileMetrics, MetricsResult}
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

        val toolResults: Seq[ResultsUploader.ToolResults] = issuesToolExecutorResult.flatMap {
          case IssuesToolExecutorResult(toolName, files, Success(results)) =>
            Option(ResultsUploader.ToolResults(toolName, files, results))
          case IssuesToolExecutorResult(toolName, _, Failure(err)) =>
            logger.warn(s"Skipping upload for $toolName since analysis failed: ${err.getMessage}")
            Option.empty[ResultsUploader.ToolResults]
        }

        val languageAndToolResults: Seq[(String, Either[Throwable, Set[FileMetrics]])] =
          metricsToolExecutorResult.flatMap {
            case MetricsToolExecutorResult(language, Success(fileMetrics)) =>
              Option((language, Right(fileMetrics)))
            case MetricsToolExecutorResult(language, Failure(err)) =>
              Option((language, Left(err)))
          }

        val metricsResults: Seq[ResultsUploader.MetricsResults] = languageAndToolResults.groupBy {
          case (language, _) => language
        }.toSeq.map {
          case (language, languageAndFileMetricsSeq) =>
            val results: Set[MetricsResult] = languageAndFileMetricsSeq.map {
              case (_, Right(fileMetrics)) =>
                MetricsResult(fileMetrics, None)
              case (_, Left(err)) =>
                MetricsResult(Set.empty, Some(err.getMessage))
            }(collection.breakOut)

            ResultsUploader.MetricsResults(language, results)
        }

        uploader.sendResults(toolResults, metricsResults)
      }.getOrElse(Future.successful(().asRight[String]))
    }).fold(err => Future.successful(err.asLeft[Unit]), identity)
  }

  private def cleanup(directoryOpt: Option[File]): Unit = {
    val directory = directoryOpt.getOrElse(File.currentWorkingDirectory) / ".codacy.json"
    directory.delete(swallowIOExceptions = true)
  }
}
