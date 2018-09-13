package com.codacy.analysis.core.files

import better.files.File

import scala.util.{Failure, Try}

class FallbackFileCollector(fileCollectorCompanions: List[FileCollectorCompanion[Try]]) extends FileCollector[Try] {

  private val fileCollectors: List[FileCollector[Try]] = fileCollectorCompanions.map(_.apply())

  override def list(directory: File): Try[FilesTarget] = {
    list(fileCollectors, directory)
  }

  private def list(fileCollectorList: List[FileCollector[Try]], directory: File): Try[FilesTarget] = {
    fileCollectorList match {
      case fileCollector :: tail =>
        fileCollector.list(directory).recoverWith {
          case _ =>
            logger.info(s"Failed to list files with ${fileCollector.getClass.getName}")
            list(tail, directory)
        }
      case Nil =>
        val errorMessage =
          s"All FileCollectors failed to list files: ${fileCollectorCompanions.map(_.name).mkString(",")}"
        logger.error(errorMessage)

        Failure(new Exception(errorMessage))
    }
  }
}

class FallbackFileCollectorCompanion(fileCollectorCompanions: List[FileCollectorCompanion[Try]])
    extends FileCollectorCompanion[Try] {

  val name: String = s"fallback:${fileCollectorCompanions.map(_.name).mkString(",")}"

  override def apply(): FileCollector[Try] = new FallbackFileCollector(fileCollectorCompanions)

}
