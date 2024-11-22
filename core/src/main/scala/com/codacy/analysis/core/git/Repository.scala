package com.codacy.analysis.core.git

import org.eclipse.jgit.api.{Git => JGit}
import org.eclipse.jgit.lib.{Repository => JGitRepository}

import scala.collection.JavaConverters._
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

  def uncommitedFiles: Try[Set[String]] = {
    Try {
      val status = jGit.status().call()
      val javaSet = status.getUncommittedChanges
      javaSet.addAll(status.getUntracked)
      javaSet.asScala.toSet
    }
  }

}
