package com.codacy.analysis.cli

import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.command.{AnalyseCommand, CLIApp, _}

object Main extends MainImpl()

class MainImpl(analyseCommand: Analyse => AnalyseCommand = AnalyseCommand(_, sys.env),
               validateConfigurationCommand: ValidateConfiguration => ValidateConfigurationCommand =
                 ValidateConfigurationCommand(_))
    extends CLIApp {

  override def run(command: CommandOptions): ExitStatus.ExitCode = {
    runCommand(command)
  }

  def runCommand(command: CommandOptions): ExitStatus.ExitCode = {
    command match {
      case analyse: Analyse => analyseCommand(analyse).run()
      case validateConfiguration: ValidateConfiguration =>
        validateConfigurationCommand(validateConfiguration).run()
    }
  }

}
