package com.codacy.analysis.cli.files

import better.files.File
import com.codacy.analysis.cli.configuration.CodacyConfigurationFile

import scala.util.Try

case class FilesTarget(directory: File, files: Set[File])

class FileSystemFileCollector extends FileCollector[Try] {

  override def list(tool: String,
                    directory: File,
                    localConfiguration: CodacyConfigurationFile,
                    remoteConfiguration: AnyRef): Try[FilesTarget] = {
    Try {
      val baseDirectory = if (directory.isDirectory) directory else directory.parent

      val filesToAnalyse = if (directory.isDirectory) {
        directory
          .listRecursively()
          .filter(_.isRegularFile)
          .map { file =>
            file
          }(collection.breakOut): Set[File]
      } else {
        Set(directory)
      }

      FilesTarget(baseDirectory, filesToAnalyse)
    }
  }

}

object FileSystemFileCollector extends FileCollectorCompanion[Try] {

  val name: String = "file-system"

  override def apply(): FileCollector[Try] = new FileSystemFileCollector()

}
