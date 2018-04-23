package com.codacy.analysis.cli

import com.codacy.analysis.cli.analysis.Analyser
import com.codacy.analysis.cli.clients.{CodacyClient, Credentials}
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.command.{Analyse, CLIApp, Command}
import com.codacy.analysis.cli.configuration.{Environment, RemoteConfigurationFetcher}
import com.codacy.analysis.cli.files.FileCollector
import com.codacy.analysis.cli.formatter.Formatter
import cats._
import com.codacy.analysis.cli.clients.api.ProjectConfiguration
import implicits._

import scala.util.Try

object Main extends MainImpl

class MainImpl extends CLIApp {

  def run(command: Command): Unit = {
    command match {
      case analyse: Analyse =>
        val formatter: Formatter = Formatter(analyse.format, analyse.output)
        val analyser: Analyser[Try] = Analyser(analyse.extras.analyser)
        val fileCollector: FileCollector[Try] = FileCollector.defaultCollector()
        val environment = new Environment(sys.env)

        val remoteProjectConfiguration: Either[String, ProjectConfiguration] = Credentials
          .getCredentials(environment, analyse.api)
          .fold {
            "No credentials found.".asLeft[ProjectConfiguration]
          } { credentials =>
            val remoteConfigFetcher =
              new RemoteConfigurationFetcher(CodacyClient.apply(credentials))
            remoteConfigFetcher.getRemoteConfiguration(credentials, analyse)
          }
        new AnalyseExecutor(analyse, formatter, analyser, fileCollector, remoteProjectConfiguration).run()
    }
  }

}
