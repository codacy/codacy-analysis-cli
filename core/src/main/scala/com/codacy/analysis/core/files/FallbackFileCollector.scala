package com.codacy.analysis.core.files

import better.files.File
import com.codacy.analysis.core.utils.IOHelper.IOThrowable
import scalaz.zio.IO

class FallbackFileCollector(fileCollectorCompanions: List[FileCollectorCompanion[IOThrowable]])
    extends FileCollector[IOThrowable] {

  private val fileCollectors: List[FileCollector[IOThrowable]] = fileCollectorCompanions.map(_.apply())

  override def list(directory: File): IOThrowable[FilesTarget] = {
    list(fileCollectors, directory)
  }

  private def list(fileCollectorList: List[FileCollector[IOThrowable]], directory: File): IOThrowable[FilesTarget] = {
    fileCollectorList match {
      case fileCollector :: tail =>
        fileCollector
          .list(directory)
          .redeem({
            case _ =>
              logger.info(s"Failed to list files with ${fileCollector.getClass.getName}")
              list(tail, directory)
          }, IO.point(_))
      case Nil =>
        val errorMessage =
          s"All FileCollectors failed to list files: ${fileCollectorCompanions.map(_.name).mkString(",")}"
        logger.error(errorMessage)

        IO.fail(new Exception(errorMessage))
    }
  }
}

class FallbackFileCollectorCompanion(fileCollectorCompanions: List[FileCollectorCompanion[IOThrowable]])
    extends FileCollectorCompanion[IOThrowable] {

  val name: String = s"fallback:${fileCollectorCompanions.map(_.name).mkString(",")}"

  override def apply(): FileCollector[IOThrowable] = new FallbackFileCollector(fileCollectorCompanions)

}
