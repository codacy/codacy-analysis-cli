package com.codacy.analysis.core.files

import java.nio.file.{Path, Paths}

import better.files.File
import com.codacy.analysis.core.clients.api.ProjectConfiguration
import com.codacy.analysis.core.configuration.CodacyConfigurationFile
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk

import scala.util.Try

class GitFileCollector extends FileCollector[Try] {
  override def list(directory: File,
                    localConfiguration: Either[String, CodacyConfigurationFile],
                    remoteConfiguration: Either[String, ProjectConfiguration]): Try[FilesTarget] = {

    Try {
      val builder = new FileRepositoryBuilder
      val repository = builder
        .setGitDir((directory / ".git").toJava)
        .readEnvironment
        .findGitDir
        .build

      val git = new Git(repository)

      val gitLog = git.log().setMaxCount(1).call()
      val commit = gitLog.iterator().next()

      val treeWalk = new TreeWalk(repository)

      treeWalk.addTree(new RevWalk(repository).parseTree(commit))

      val pathBuffer = collection.mutable.HashSet[Path]()

      while (treeWalk.next) {
        val path = treeWalk.getPathString
        pathBuffer += Paths.get(path)
      }

      treeWalk.close()

      val filteredFiles = defaultFilter(pathBuffer.toSet, localConfiguration, remoteConfiguration)

      val checkedFiles = checkPermissions(directory, filteredFiles)

      FilesTarget(directory, checkedFiles.readableFiles, checkedFiles.unreadableFiles)
    }
  }
}

object GitFileCollector extends FileCollectorCompanion[Try] {

  val name: String = "git"

  override def apply(): FileCollector[Try] = new GitFileCollector()

}
