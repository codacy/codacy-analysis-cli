package com.codacy.analysis.cli

import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.command.{AnalyseCommand, CLIApp, _}
import scalaz.zio.IO

object Main extends MainImpl()

class MainImpl(analyseCommand: Analyse => IO[Nothing, AnalyseCommand] = AnalyseCommand(_, sys.env),
               validateConfigurationCommand: ValidateConfiguration => ValidateConfigurationCommand =
                 ValidateConfigurationCommand(_))
    extends CLIApp {

  override def run(command: CommandOptions): IO[Nothing, ExitStatus.ExitCode] = {
    runCommand(command)
  }

  def runCommand(command: CommandOptions): IO[Nothing, ExitStatus.ExitCode] = {
    command match {
      case analyse: Analyse                             => analyseCommand(analyse).flatMap(_.run())
      case validateConfiguration: ValidateConfiguration => validateConfigurationCommand(validateConfiguration).run()
    }
  }

}
