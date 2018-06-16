package com.codacy.analysis.cli.command

import better.files.File
import com.codacy.analysis.cli.analysis.ExitStatus.ExitCodes
import com.codacy.analysis.cli.configuration.CodacyConfigurationFile


class ValidateConfigurationExecutor(validateConfiguration: ValidateConfiguration) {

  def run(): CLIApp#ExitCode = {
    val directory = validateConfiguration.directory.getOrElse(File.currentWorkingDirectory)

    CodacyConfigurationFile.search(directory)
      .flatMap(CodacyConfigurationFile.load) match {
      case Left(e) =>
        Console.err.println(e)
        ExitCodes.failedAnalysis

      case Right(configuration) =>
        pprint.pprintln(configuration)
        ExitCodes.success
    }
  }

}
