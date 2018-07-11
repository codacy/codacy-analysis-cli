package com.codacy.analysis.cli

import better.files._
import cats.implicits._
import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.clients.Credentials
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor.ExecutorResult
import com.codacy.analysis.cli.command.{Analyse, CLIApp, Command}
import com.codacy.analysis.cli.configuration.Environment
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.core.analysis.Analyser
import com.codacy.analysis.core.clients.CodacyClient
import com.codacy.analysis.core.clients.api.ProjectConfiguration
import com.codacy.analysis.core.files.FileCollector
import com.codacy.analysis.core.upload.ResultsUploader
import com.codacy.analysis.core.utils.Logger
import org.log4s.getLogger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

object Main extends MainImpl

class MainImpl extends CLIApp {

  private val logger: org.log4s.Logger = getLogger

  def run(command: Command): Unit = {
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

        exit(
          new ExitStatus(analyse.maxAllowedIssues, analyse.failIfIncompleteValue)
            .exitCode(analysisResults, uploadResult))
    }

    ()
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
        val resultsToUpload = executorResults.flatMap {
          case ExecutorResult(toolName, files, Success(results)) =>
            logger.info(s"Going to upload ${results.size} results for $toolName")
            Option(ResultsUploader.ToolResults(toolName, files, results))

          case ExecutorResult(toolName, _, Failure(err)) =>
            logger.warn(s"Skipping upload for $toolName since analysis failed: ${err.getMessage}")
            Option.empty[ResultsUploader.ToolResults]
        }

        uploader.sendResults(resultsToUpload)
      }.getOrElse(Future.successful(().asRight[String]))
    }).fold(err => Future.successful(err.asLeft[Unit]), identity)
  }

  private def cleanup(directoryOpt: Option[File]): Unit = {
    val directory = directoryOpt.getOrElse(File.currentWorkingDirectory) / ".codacy.json"
    directory.delete(swallowIOExceptions = true)
  }
}
