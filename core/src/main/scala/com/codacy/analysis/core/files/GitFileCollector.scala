package com.codacy.analysis.core.files

import better.files.File
import com.codacy.analysis.core.clients.api.ProjectConfiguration
import com.codacy.analysis.core.configuration.CodacyConfigurationFile
import com.codacy.analysis.core.git.Git

import scala.util.Try

class GitFileCollector extends FileCollector[Try] {

  override def list(directory: File,
                    localConfiguration: Either[String, CodacyConfigurationFile],
                    remoteConfiguration: Either[String, ProjectConfiguration]): Try[FilesTarget] = {

    for {
      repository <- Git.repository(directory)
      latestCommit <- repository.latestCommit
      allFiles <- latestCommit.files
    } yield {
      val filteredFiles = defaultFilter(allFiles, localConfiguration, remoteConfiguration)
      val checkedFiles = checkPermissions(directory, filteredFiles)

      FilesTarget(directory, checkedFiles.readableFiles, checkedFiles.unreadableFiles)
    }
  }
}

object GitFileCollector extends FileCollectorCompanion[Try] {

  val name: String = "git"

  override def apply(): FileCollector[Try] = new GitFileCollector()

}
