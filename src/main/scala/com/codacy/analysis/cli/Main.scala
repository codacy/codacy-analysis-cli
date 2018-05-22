package com.codacy.analysis.cli

import cats.implicits._
import com.codacy.analysis.cli.analysis.{Analyser, Status}
import com.codacy.analysis.cli.clients.api.ProjectConfiguration
import com.codacy.analysis.cli.clients.{CodacyClient, Credentials}
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor.ExecutorResult
import com.codacy.analysis.cli.command.{Analyse, CLIApp, Command}
import com.codacy.analysis.cli.configuration.Environment
import com.codacy.analysis.cli.files.FileCollector
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.cli.upload.ResultsUploader
import com.codacy.analysis.cli.utils.{EitherOps, Logger}
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
          analyse.parallel).run()

        val uploadResult = uploadResults(codacyClientOpt)(analyse.uploadValue, analyse.commitUuid, analysisResults)
        Await.result(uploadResult, Duration.Inf) match {
          case Left(err) if analyse.uploadValue =>
            logger.error(s"Upload of results failed: $err")
          case Right(_) if analyse.uploadValue =>
            logger.info("Completed upload of results to API")
          case _ =>
            logger.info("Skipping upload of results to API")
        }

        System.exit(new Status(analyse.maxAllowedIssues).exitCode(analysisResults))
    }

    ()
  }

  private def uploadResults(codacyClientOpt: Option[CodacyClient])(
    upload: Boolean,
    commitUuid: Option[String],
    executorResultsEither: Either[String, Seq[ExecutorResult]]): Future[Either[String, Unit]] = {
    val resultsUploader: Either[String, ResultsUploader] = codacyClientOpt.fold {
      "No credentials found.".asLeft[ResultsUploader]
    } { codacyClient =>
      commitUuid.fold {
        "No commit option found.".asLeft[ResultsUploader]
      } { commit =>
        if (upload) {
          new ResultsUploader(commit, codacyClient, None).asRight[String]
        } else {
          "Upload option disabled.".asLeft[ResultsUploader]
        }
      }
    }

    (for {
      uploader <- resultsUploader
      executorResults <- executorResultsEither
    } yield {
      val uploadResults = executorResults.map {
        case ExecutorResult(toolName, Success(results)) =>
          logger.info(s"Going to upload ${results.size} results for $toolName")
          uploader.sendResults(toolName, results)

        case ExecutorResult(toolName, Failure(err)) =>
          logger.warn(s"Skipping upload for $toolName since analysis failed: ${err.getMessage}")
          Future.successful(().asRight[String])
      }

      Future.sequence(uploadResults).map(EitherOps.sequenceWithFixedLeftUnit("Failed upload of results")(_))
    }).fold(err => Future.successful(err.asLeft[Unit]), identity)
  }

}
