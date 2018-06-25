package com.codacy.analysis.core.git

import better.files.File
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

import scala.util.Try

object Git {

  def repository(directory: File): Try[Repository] = {
    Try {
      (directory / ".git").toJava
    }.filter { gitDir =>
      new FileRepository(gitDir.getPath).getObjectDatabase.exists()
    }.flatMap { gitDir =>
      Try {
        val builder = new FileRepositoryBuilder

        val repository = builder.setGitDir(gitDir).readEnvironment.findGitDir.build

        new Repository(repository)
      }
    }
  }

}
