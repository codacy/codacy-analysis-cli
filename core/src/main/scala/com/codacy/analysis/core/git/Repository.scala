package com.codacy.analysis.core.git

import com.codacy.analysis.core.utils.IOHelper.IOThrowable
import org.eclipse.jgit.api.{Git => JGit}
import org.eclipse.jgit.lib.{Repository => JGitRepository}
import scalaz.zio.IO

import scala.collection.JavaConverters._

class Repository(repository: JGitRepository) {

  val jGit: JGit = new JGit(repository)

  def latestCommit: IOThrowable[Commit] = {
    IO.syncException {
      val gitLog = jGit.log().setMaxCount(1).call()

      val revCommit = gitLog.iterator().next()

      new Commit(repository, revCommit)
    }
  }

  def uncommitedFiles: IOThrowable[Set[String]] = {
    IO.syncException {
      (jGit.status().call().getUncommittedChanges.asScala ++
        jGit.status().call().getUntracked.asScala).toSet
    }
  }

}
