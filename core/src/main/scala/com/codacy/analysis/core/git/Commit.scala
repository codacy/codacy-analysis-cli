package com.codacy.analysis.core.git

import java.nio.file.{Path, Paths}

import org.eclipse.jgit.lib.{Repository => JGitRepository}
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.eclipse.jgit.treewalk.TreeWalk

import scala.util.Try

class Commit(repository: JGitRepository, revCommit: RevCommit) {

  def files: Try[Set[Path]] = {
    Try {
      val treeWalk = new TreeWalk(repository)
      treeWalk.setRecursive(true)

      treeWalk.addTree(new RevWalk(repository).parseTree(revCommit))

      val pathBuffer = collection.mutable.HashSet[Path]()

      while (treeWalk.next) {
        val path = treeWalk.getPathString
        pathBuffer += Paths.get(path)
      }

      treeWalk.close()

      pathBuffer.toSet
    }
  }

}
