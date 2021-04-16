package com.codacy.analysis.core.storage

import better.files.File
import io.circe._
import io.circe.syntax._
import org.log4s.{Logger, getLogger}

import scala.util.{Failure, Success, Try}

abstract class FileDataStorage[T](val currentWorkingDirectory: File, val storageFilename: String) {

  implicit val encoder: Encoder[T]
  implicit val decoder: Decoder[T]

  private val logger: Logger = getLogger

  private val cacheFolder: File = {
    val defaultFolder = currentWorkingDirectory / ".codacy" / "codacy-analysis-cli"
    val cacheFolderOpt = sys.env.get("CODACY_CACHE_FOLDER").map(File(_))
    val result = cacheFolderOpt.getOrElse(defaultFolder)

    result.createIfNotExists(asDirectory = true, createParents = true)
    result
  }

  val storageFile: File = cacheFolder / storageFilename

  private def writeToFile(content: String): Try[File] =
    Try {
      storageFile.write(content)
    }

  private def readFromFile(): Try[String] =
    Try {
      storageFile.contentAsString
    }

  def invalidate(): Try[Unit] =
    Try {
      logger.debug("Invalidating storage")
      storageFile.delete()
    }

  def save(values: Seq[T]): Boolean = {
    logger.debug(s"Saving new values to storage $storageFile")
    val storageListJson = values.asJson.toString
    writeToFile(storageListJson) match {
      case Success(_) =>
        logger.debug(s"Storage saved successfully")
        true
      case Failure(exception) =>
        logger.debug(s"Storage failed to save: ${exception.getLocalizedMessage}")
        false
    }
  }

  def get(): Option[Seq[T]] = {
    logger.debug("Retrieving storage")
    if (storageFile.exists) {
      val fileContentOpt = readFromFile() match {
        case Success(content) => Some(content)
        case Failure(exception) =>
          logger.debug(s"Failed to retrieve from storage: ${exception.getLocalizedMessage}")
          None
      }
      fileContentOpt.flatMap { content =>
        parser.decode[Seq[T]](content).fold(_ => None, v => Some(v)).filter(_.nonEmpty)
      }
    } else {
      None
    }
  }
}
