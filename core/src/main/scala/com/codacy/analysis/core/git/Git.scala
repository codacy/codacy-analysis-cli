package com.codacy.analysis.core.git

import better.files.File
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.log4s.{Logger, getLogger}

import scala.util.Try

object Git {

  private val logger: Logger = getLogger

  def repository(directory: File): Try[Repository] = {
    Try((directory / ".git").toJava).filter(d => new FileRepository(d.getPath).getObjectDatabase.exists()).flatMap {
      gitDir =>
        Try {
          val builder = new FileRepositoryBuilder

          val repository = builder.setGitDir(gitDir).readEnvironment.findGitDir.build

          new Repository(repository)
        }
    }
  }

  def currentCommitUuid(directory: File): Option[Commit.Uuid] = {
    Git
      .repository(directory)
      .flatMap(_.latestCommit)
      .fold(
        { e =>
          logger.warn(e)(e.getMessage)
          None
        },
        commit => Some(commit.commitUuid))
  }

}
