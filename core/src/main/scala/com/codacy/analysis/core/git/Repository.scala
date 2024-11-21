package com.codacy.analysis.core.git

import org.eclipse.jgit.api.{Git => JGit}
import org.eclipse.jgit.lib.{Repository => JGitRepository}
import org.eclipse.jgit.api.errors.GitAPIException;

import scala.collection.JavaConverters._
import scala.util.Try

class Repository(repository: JGitRepository) {

  val jGit: JGit = new JGit(repository)

  def latestCommit: Try[Commit] = {
    try {
      val gitLog = jGit.log().setMaxCount(1).call()

      val revCommit = gitLog.iterator().next()

      new Commit(repository, revCommit)
    } catch (GitAPIException | IOException e) {
      System.err.println("An error occurred while getting the latest commit: " + e.getMessage());
      e.printStackTrace();
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
