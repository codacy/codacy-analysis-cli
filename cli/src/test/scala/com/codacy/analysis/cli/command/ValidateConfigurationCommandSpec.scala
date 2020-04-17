package com.codacy.analysis.cli.command

import better.files.{File, Resource}
import com.codacy.analysis.cli.analysis.ExitStatus.ExitCodes
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.FileMatchers
import org.specs2.mutable.Specification

class ValidateConfigurationCommandSpec extends Specification with NoLanguageFeatures with FileMatchers {

  "ValidateConfigurationExecutor" should {
    "find configuration file" in {
      File
        .temporaryDirectory()
        .map { directory =>
          val resource = Resource.getAsString("com/codacy/analysis/core/configuration/codacy.yaml")
          (directory / ".codacy.yaml").write(resource)

          val command =
            ValidateConfigurationCommand(ValidateConfiguration(CommonOptions(), Option(directory)))

          command.run() mustEqual ExitCodes.success
        }
        .get
    }

    "fail" in {
      File
        .temporaryDirectory()
        .map { directory =>
          val command =
            ValidateConfigurationCommand(ValidateConfiguration(CommonOptions(), Option(directory)))

          command.run() mustEqual ExitCodes.invalidConfigurationFile
        }
        .get
    }
  }

}
