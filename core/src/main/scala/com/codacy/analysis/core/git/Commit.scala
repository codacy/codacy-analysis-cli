package com.codacy.analysis.core.git

import java.nio.file.{Path, Paths}

import com.codacy.analysis.core.utils.IOHelper.IOThrowable
import org.eclipse.jgit.lib.{Repository => JGitRepository}
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.eclipse.jgit.treewalk.TreeWalk
import scalaz.zio.IO

class Commit(repository: JGitRepository, revCommit: RevCommit) {

  def files: IOThrowable[Set[Path]] = {
    IO.syncException {
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

  val commitUuid: Commit.Uuid = Commit.Uuid(revCommit.name())
}

object Commit {
  final case class Uuid(value: String) extends AnyVal {
    override def toString: String = value
  }
}
