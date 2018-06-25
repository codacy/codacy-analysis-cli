package com.codacy.analysis.core.git

import org.eclipse.jgit.api.{Git => JGit}
import org.eclipse.jgit.lib.{Repository => JGitRepository}

import scala.util.Try

class Repository(repository: JGitRepository) {

  val jGit: JGit = new JGit(repository)

  def latestCommit: Try[Commit] = {
    Try {
      val gitLog = jGit.log().setMaxCount(1).call()

      val revCommit = gitLog.iterator().next()

      new Commit(repository, revCommit)
    }
  }

  def hasUncommittedChanges: Try[Boolean] = {
    Try {
      jGit.status().call().hasUncommittedChanges
    }
  }

}
