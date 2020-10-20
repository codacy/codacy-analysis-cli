package com.codacy.analysis.core.storage

import better.files.File
import io.circe._
import io.circe.syntax._
import org.log4s.{Logger, getLogger}

import scala.util.Try

trait FileDataStorage[T] {
  private val logger: Logger = getLogger

  implicit val encoder: Encoder[T]
  implicit val decoder: Decoder[T]

  def storageFilename: String

  val storageFile: File = File.currentWorkingDirectory / ".codacy" / "codacy-analysis-cli" / storageFilename

  private def writeToFile(file: File, content: String): Try[File] =
    synchronized {
      Try {
        file.createFileIfNotExists(createParents = true)
        file.write(content)
      }
    }

  private def readFromFile(file: File): String =
    synchronized {
      file.contentAsString
    }

  def invalidate(): Boolean =
    synchronized {
      logger.debug("Invalidating storage")
      !storageFile.delete().exists
    }

  def save(values: Seq[T]): Boolean = {
    logger.debug("Saving new values to storage")
    val storageListJson = values.asJson.toString
    val wroteSuccessfully = writeToFile(storageFile, storageListJson)
    logger.debug(s"Storage saved with status: ${wroteSuccessfully}")
    wroteSuccessfully.isSuccess
  }

  def get(): Option[Seq[T]] = {
    logger.debug("Retrieving storage")
    if (storageFile.exists) {
      parser.decode[Seq[T]](readFromFile(storageFile)).fold(_ => None, v => Some(v)).filter(_.nonEmpty)
    } else {
      None
    }
  }
}
