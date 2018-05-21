package com.codacy.analysis.cli

import cats.implicits._
import com.codacy.analysis.cli.analysis.Analyser
import com.codacy.analysis.cli.clients.api.ProjectConfiguration
import com.codacy.analysis.cli.clients.{CodacyClient, Credentials}
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.command.{Analyse, CLIApp, Command}
import com.codacy.analysis.cli.configuration.Environment
import com.codacy.analysis.cli.files.FileCollector
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.cli.upload.ResultsUploader
import com.codacy.analysis.cli.utils.Logger
import org.log4s.getLogger

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.Try

object Main extends MainImpl

class MainImpl extends CLIApp {

  private val logger: org.log4s.Logger = getLogger

  def run(command: Command): Unit = {
    val res = command match {
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

        val resultsUploader: Either[String, ResultsUploader] = codacyClientOpt.fold {
          "No credentials found.".asLeft[ResultsUploader]
        } { codacyClient =>
          analyse.commitUuid.fold {
            "No commit option found.".asLeft[ResultsUploader]
          } { commit =>
            if (analyse.uploadValue) {
              new ResultsUploader(commit, codacyClient, None).asRight[String]
            } else {
              "Upload option disabled.".asLeft[ResultsUploader]
            }
          }

        }

        new AnalyseExecutor(
          analyse.tool,
          analyse.directory,
          formatter,
          analyser,
          resultsUploader,
          fileCollector,
          remoteProjectConfiguration,
          analyse.parallel).run()
    }

    Await.result(res, Duration.Inf) match {
      case Left(e) =>
        logger.error(s"Failed analysis: $e")
      case Right(_) =>
        logger.info("Completed analysis successfully")
    }

    ()
  }

}
