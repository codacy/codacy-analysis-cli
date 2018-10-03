package com.codacy.analysis.core.files

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.core.utils.IOHelper.IOThrowable
import scalaz.zio.IO

final case class FilesTarget(directory: File, readableFiles: Set[Path], unreadableFiles: Set[Path])

private[files] final case class CheckedFiles(readableFiles: Set[Path], unreadableFiles: Set[Path])

class FileSystemFileCollector extends FileCollector[IOThrowable] {

  override def list(directory: File): IOThrowable[FilesTarget] = {
    IO.syncException {
      val allFiles =
        directory
          .listRecursively()
          .collect { case f if f.isRegularFile => directory.relativize(f) }
          .filterNot(_.startsWith(".git"))
          .to[Set]

      val checkedFiles = checkPermissions(directory, allFiles)

      FilesTarget(directory, checkedFiles.readableFiles, checkedFiles.unreadableFiles)
    }
  }

}

object FileSystemFileCollector extends FileCollectorCompanion[IOThrowable] {

  val name: String = "file-system"

  override def apply(): FileCollector[IOThrowable] = new FileSystemFileCollector()

}
