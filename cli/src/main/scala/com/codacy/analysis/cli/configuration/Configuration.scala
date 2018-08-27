package com.codacy.analysis.cli.configuration

import better.files.File
import com.codacy.analysis.cli.command.Properties

object Configuration {

  def baseProjectDirectory(directory: Option[File]): File =
    directory.fold(Properties.codacyCode.getOrElse(File.currentWorkingDirectory))(dir =>
      if (dir.isDirectory) dir else dir.parent)
}
