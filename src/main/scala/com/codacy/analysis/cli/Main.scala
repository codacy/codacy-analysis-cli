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

        val uploadResultFut = uploadResults(codacyClientOpt)(analyse.uploadValue, analyse.commitUuid, analysisResults)
        val uploadResult = Try(Await.result(uploadResultFut, Duration.Inf)) match {
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

        System.exit(
          new Status(analyse.maxAllowedIssues, analyse.failIfIncompleteValue).exitCode(analysisResults, uploadResult))
    }

    ()
  }

  private def uploadResults(codacyClientOpt: Option[CodacyClient])(
    upload: Boolean,
    commitUuid: Option[String],
    executorResultsEither: Either[String, Seq[ExecutorResult]]): Future[Either[String, Unit]] = {
    (for {
      uploaderOpt <- retrieveUploader(codacyClientOpt, upload, commitUuid)
      executorResults <- executorResultsEither
    } yield {
      uploaderOpt.map { uploader =>
        val uploadResults = executorResults.map {
          case ExecutorResult(toolName, Success(results)) =>
            logger.info(s"Going to upload ${results.size} results for $toolName")
            uploader.sendResults(toolName, results)

          case ExecutorResult(toolName, Failure(err)) =>
            logger.warn(s"Skipping upload for $toolName since analysis failed: ${err.getMessage}")
            Future.successful(().asRight[String])
        }

        Future.sequence(uploadResults).map(EitherOps.sequenceUnitWithFixedLeft("Failed upload of results")(_))
      }.getOrElse(Future.successful(().asRight[String]))
    }).fold(err => Future.successful(err.asLeft[Unit]), identity)
  }

  private def retrieveUploader(codacyClientOpt: Option[CodacyClient],
                               upload: Boolean,
                               commitUuid: Option[String]): Either[String, Option[ResultsUploader]] = {
    if (upload) {
      codacyClientOpt.fold {
        "No credentials found.".asLeft[Option[ResultsUploader]]
      } { codacyClient =>
        commitUuid.fold {
          "No commit found.".asLeft[Option[ResultsUploader]]
        } { commit =>
          Option(new ResultsUploader(commit, codacyClient, None)).asRight[String]
        }
      }
    } else {
      logger.info(s"Upload step disabled")
      Option.empty[ResultsUploader].asRight[String]
    }
  }
}
