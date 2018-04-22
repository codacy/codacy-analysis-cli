package com.codacy.analysis.cli

import com.codacy.analysis.cli.analysis.Analyser
import com.codacy.analysis.cli.clients.{CodacyClient, Credentials}
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.command.{Analyse, CLIApp, Command}
import com.codacy.analysis.cli.configuration.{Environment, RemoteConfigurationFetcher}
import com.codacy.analysis.cli.files.FileCollector
import com.codacy.analysis.cli.formatter.Formatter
import org.log4s.{Logger, getLogger}

import scala.util.Try

object Main extends MainImpl

class MainImpl extends CLIApp {

  def run(command: Command): Unit = {
    command match {
      case analyse: Analyse =>
        utils.Logger.setLevel(analyse.options.verbose.## > 0)
        val logger: Logger = getLogger

        val formatter: Formatter = Formatter(analyse.format, analyse.output)
        val analyser: Analyser[Try] = Analyser(analyse.extras.analyser)
        val fileCollector: FileCollector[Try] = FileCollector.defaultCollector()
        val environment = new Environment(sys.env)

        Credentials.getCredentials(environment, analyse.api).fold(logger.warn("No credentials found.")) { credentials =>
          val remoteConfigFetcher =
            new RemoteConfigurationFetcher(credentials, CodacyClient.apply(credentials), analyse)
          new AnalyseExecutor(analyse, formatter, analyser, fileCollector, remoteConfigFetcher).run()
          ()
        }
    }
  }

}
