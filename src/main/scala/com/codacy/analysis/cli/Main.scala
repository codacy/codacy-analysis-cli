package com.codacy.analysis.cli

import com.codacy.analysis.cli.analysis.Analyser
import com.codacy.analysis.cli.clients.{CodacyClient, Credentials}
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.command.{Analyse, CLIApp, Command}
import com.codacy.analysis.cli.configuration.Environment
import com.codacy.analysis.cli.files.FileCollector
import com.codacy.analysis.cli.formatter.Formatter
import cats._
import com.codacy.analysis.cli.clients.api.ProjectConfiguration
import com.codacy.analysis.cli.upload.ResultsUploader
import com.codacy.analysis.cli.utils.Logger
import implicits._
import org.log4s.getLogger
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.Try

object Main extends MainImpl

class MainImpl extends CLIApp {

  val logger: org.log4s.Logger = getLogger

  def run(command: Command): Unit = {
    val res = command match {
      case analyse: Analyse =>
        Logger.setLevel(analyse.options.verbose.## > 0)
        val formatter: Formatter = Formatter(analyse.format, analyse.output)
        val analyser: Analyser[Try] = Analyser(analyse.extras.analyser)
        val fileCollector: FileCollector[Try] = FileCollector.defaultCollector()
        val environment = new Environment(sys.env)
        val codacyClientOpt: Option[CodacyClient] = Credentials.get(environment, analyse.api).map { credentials =>
          CodacyClient.apply(credentials)
        }

        val remoteProjectConfiguration: Either[String, ProjectConfiguration] = codacyClientOpt.fold {
          "No credentials found.".asLeft[ProjectConfiguration]
        } { codacyClient =>
          codacyClient.getRemoteConfiguration
        }

        val resultsUploader: Either[String, ResultsUploader] = codacyClientOpt.fold {
          "No credentials found.".asLeft[ResultsUploader]
        } { codacyClient =>
          analyse.commit.fold {
            "No commit option found.".asLeft[ResultsUploader]
          } { commit =>
            if (analyse.upload) {
              new ResultsUploader(commit, codacyClient, None).asRight[String]
            } else {
              "Upload option disabled.".asLeft[ResultsUploader]
            }
          }

        }

        new AnalyseExecutor(analyse, formatter, analyser, resultsUploader, fileCollector, remoteProjectConfiguration)
          .run()
    }

    Await.result(res, Duration.Inf) match {
      case Left(_) =>
        logger.error("Upload of results failed")
      case Right(_) =>
        logger.info("Completed upload of results to API")
    }
    ()
  }

}
