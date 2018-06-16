package com.codacy.analysis.cli.command

import better.files.File
import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.analysis.ExitStatus.ExitCodes
import com.codacy.analysis.core.configuration.CodacyConfigurationFile

class ValidateConfigurationExecutor(validateConfiguration: ValidateConfiguration) {

  def run(): ExitStatus.ExitCode = {
    val directory = validateConfiguration.directory.getOrElse(File.currentWorkingDirectory)

    new CodacyConfigurationFile.Loader().load(directory) match {
      case Left(e) =>
        Console.err.println(e)
        ExitCodes.invalidConfigurationFile

      case Right(configuration) =>
        pprint.pprintln(configuration)
        ExitCodes.success
    }
  }

}
