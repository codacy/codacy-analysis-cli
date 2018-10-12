package com.codacy.analysis.cli.command

import better.files.File
import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.analysis.ExitStatus.ExitCodes
import com.codacy.analysis.core.configuration.CodacyConfigurationFileLoader
import scalaz.zio.IO

object ValidateConfigurationCommand {

  def apply(validateConfiguration: ValidateConfiguration): ValidateConfigurationCommand = {
    new ValidateConfigurationCommand(validateConfiguration, new CodacyConfigurationFileLoader())
  }
}

class ValidateConfigurationCommand(validateConfiguration: ValidateConfiguration,
                                   configurationLoader: CodacyConfigurationFileLoader) {

  def run(): IO[Nothing, ExitStatus.ExitCode] = {
    val directory = validateConfiguration.directory.getOrElse(File.currentWorkingDirectory)

    (for {
      file <- configurationLoader.search(directory)
      cfgFile <- configurationLoader.parse(file.contentAsString)
    } yield (file, cfgFile)).redeemPure({ e =>
      Console.err.println(e)
      ExitCodes.invalidConfigurationFile
    }, {
      case (file, cfgFile) =>
        Console.out.println(s"Successfully loaded the Codacy configuration file in ${file.pathAsString}")
        pprint.pprintln(cfgFile)
        ExitCodes.success
    })
  }

}
