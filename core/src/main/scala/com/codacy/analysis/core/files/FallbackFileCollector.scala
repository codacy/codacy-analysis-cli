package com.codacy.analysis.core.files

import better.files.File
import cats.MonadError

class FallbackFileCollector[T[_]](fileCollectorCompanions: List[FileCollectorCompanion[T]])(implicit
  monadError: MonadError[T, Throwable])
    extends FileCollector[T] {

  private val fileCollectors: List[FileCollector[T]] = fileCollectorCompanions.map(_.apply())

  override def list(directory: File): T[FilesTarget] = {
    list(fileCollectors, directory)
  }

  private def list(fileCollectorList: List[FileCollector[T]], directory: File): T[FilesTarget] = {
    fileCollectorList match {
      case fileCollector :: tail =>
        monadError.recoverWith(fileCollector.list(directory)) {
          case _ =>
            logger.info(s"Failed to list files with ${fileCollector.getClass.getName}")
            list(tail, directory)
        }
      case Nil =>
        val errorMessage =
          s"All FileCollectors failed to list files: ${fileCollectorCompanions.map(_.name).mkString(",")}"
        logger.error(errorMessage)

        monadError.raiseError(new Exception(errorMessage))
    }
  }
}

class FallbackFileCollectorCompanion[T[_]](fileCollectorCompanions: List[FileCollectorCompanion[T]])(implicit
  monadError: MonadError[T, Throwable])
    extends FileCollectorCompanion[T] {

  val name: String = s"fallback:${fileCollectorCompanions.map(_.name).mkString(",")}"

  override def apply(): FileCollector[T] = new FallbackFileCollector(fileCollectorCompanions)

}
