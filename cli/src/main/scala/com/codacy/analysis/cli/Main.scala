package com.codacy.analysis.cli

import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.command.{AnalyseCommand, CLIApp, _}
import com.codacy.analysis.core.utils.Logger

object Main extends MainImpl()

class MainImpl(analyseCommand: Analyze => AnalyseCommand = AnalyseCommand(_, sys.env),
               validateConfigurationCommand: ValidateConfiguration => ValidateConfigurationCommand =
                 ValidateConfigurationCommand(_))
    extends CLIApp {

  override def run(command: CommandOptions): ExitStatus.ExitCode = {
    Logger.setLevel(command.options.verboseValue)

    runCommand(command)
  }

  def runCommand(command: CommandOptions): ExitStatus.ExitCode = {
    command match {
      case analyze: Analyze => analyseCommand(analyze).run()
      case validateConfiguration: ValidateConfiguration =>
        validateConfigurationCommand(validateConfiguration).run()
    }
  }

}
