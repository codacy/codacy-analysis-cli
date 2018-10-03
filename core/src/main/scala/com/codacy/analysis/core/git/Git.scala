package com.codacy.analysis.core.git

import better.files.File
import com.codacy.analysis.core.utils.IOHelper.IOThrowable
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.log4s.{Logger, getLogger}
import scalaz.zio.IO

object Git {

  private val logger: Logger = getLogger

  def repository(directory: File): IOThrowable[Repository] = {
    IO.syncException {
      (directory / ".git").toJava
    }.flatMap { gitDir =>
      if (new FileRepository(gitDir.getPath).getObjectDatabase.exists()) {
        IO.syncException {
          val builder = new FileRepositoryBuilder

          val repository = builder.setGitDir(gitDir).readEnvironment.findGitDir.build

          new Repository(repository)
        }
      } else {
        IO.fail(new Exception("Not a git repository"))
      }
    }
  }

  def currentCommitUuid(directory: File): IOThrowable[Commit.Uuid] = {
    Git
      .repository(directory)
      .flatMap(_.latestCommit)
      .redeem({ e =>
        logger.warn(e)(e.getMessage)
        IO.fail(e)
      }, commit => IO.point(commit.commitUuid))
  }

}
