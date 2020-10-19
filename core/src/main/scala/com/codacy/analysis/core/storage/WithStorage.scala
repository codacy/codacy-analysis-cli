package com.codacy.analysis.core.storage

import better.files.File
import io.circe._
import io.circe.syntax._
import org.log4s.{Logger, getLogger}

abstract class FileDataStorage[T] {
  private val logger: Logger = getLogger

  implicit val encoder: Encoder[T]
  implicit val decoder: Decoder[T]

  def storageFilename: String

  val storageFile: File = File.currentWorkingDirectory / ".codacy" / "codacy-analysis-cli" / storageFilename

  def compare(current: T, value: T): Boolean

  private def writeToFile(file: File, content: String): Boolean =
    synchronized {
      file.createFileIfNotExists(createParents = true)
      file.write(content)
      file.contentAsString == content
    }

  private def readFromFile(file: File): String =
    synchronized {
      file.contentAsString
    }

  def invalidate(): Boolean = {
    logger.debug("Invalidating storage")
    !storageFile.delete().exists
  }

  def put(values: Seq[T]): Unit = {
    logger.debug("Adding values to storage")
    val newStorageList = get() match {
      case None                => values
      case Some(currentStored) => mergeSeq(values, currentStored)
    }

    val storageListJson = newStorageList.asJson.toString
    val wroteSuccessfully = writeToFile(storageFile, storageListJson)
    logger.debug(s"Storage saved with status: ${wroteSuccessfully}")
  }

  def get(): Option[Seq[T]] = {
    logger.debug("Retrieving storage")
    if (storageFile.exists) {
      parser.decode[Seq[T]](readFromFile(storageFile)).fold(_ => None, v => Some(v)).filter(_.nonEmpty)
    } else {
      None
    }
  }

  private def addValue(acc: Seq[T], value: T) = {
    // if already exists in the new list, don't add it again
    if (acc.exists(v => compare(v, value))) {
      acc
    } else {
      acc :+ value
    }
  }

  private[storage] def mergeSeq(newTools: Seq[T], storedTools: Seq[T]) = {
    logger.debug("Merging storage values with new values list")
    // newTools should be first so it updates storage for already existing tools
    val fullList = newTools ++ storedTools
    fullList.foldLeft(Seq[T]()) {
      case (acc, value) => addValue(acc, value)
    }
  }
}

trait WithStorage[T] {
  val storage: FileDataStorage[T]
}
