package com.codacy.analysis.core.files

import better.files.File
import com.codacy.analysis.core.git.Git

import com.codacy.analysis.core.utils.IOHelper.IOThrowable

class GitFileCollector extends FileCollector[IOThrowable] {

  override def list(directory: File): IOThrowable[FilesTarget] = {

    for {
      repository <- Git.repository(directory)
      latestCommit <- repository.latestCommit
      allFiles <- latestCommit.files
    } yield {
      val checkedFiles = checkPermissions(directory, allFiles)

      FilesTarget(directory, checkedFiles.readableFiles, checkedFiles.unreadableFiles)
    }
  }
}

object GitFileCollector extends FileCollectorCompanion[IOThrowable] {

  val name: String = "git"

  override def apply(): FileCollector[IOThrowable] = new GitFileCollector()

}
