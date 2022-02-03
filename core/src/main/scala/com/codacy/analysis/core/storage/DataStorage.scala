package com.codacy.analysis.core.storage

import better.files.File
import better.files.File.home
import io.circe._
import io.circe.syntax._
import org.log4s.{Logger, getLogger}

import scala.util.{Failure, Success, Try}

trait DataStorage[T] {
  def get(): Option[Seq[T]]
  def save(values: Seq[T]): Boolean
  def invalidate(): Try[Unit]
}

object FileDataStorage {

  def apply[T](storageFilename: String)(implicit encoder: Encoder[T], decoder: Decoder[T]): DataStorage[T] = {
    val cacheFolder: File = {
      val defaultFolder = File.currentWorkingDirectory / ".codacy" / "codacy-analysis-cli"
      val osNameOpt = sys.props.get("os.name").map(_.toLowerCase)
      val result = osNameOpt match {
        case Some(sysName) if sysName.contains("mac") || sysName == "darwin" =>
          home / "Library" / "Caches" / "Codacy" / "codacy-analysis-cli"

        case Some(sysName) if sysName.contains("nix") || sysName.contains("nux") =>
          home / ".cache" / "codacy" / "codacy-analysis-cli"

        case Some(sysName) if sysName.contains("windows") =>
          sys.env.get("APPDATA").fold(defaultFolder) { windowsCacheDir =>
            File(windowsCacheDir) / "Codacy" / "codacy-analysis-cli"
          }

        case _ => defaultFolder
      }

      result.createIfNotExists(asDirectory = true, createParents = true)
      result
    }
    apply[T](cacheFolder / storageFilename)
  }

  def apply[T](storageFile: File)(implicit encoder: Encoder[T], decoder: Decoder[T]): DataStorage[T] =
    new FileDataStorage(storageFile)
}

private class FileDataStorage[T] private[storage] (storageFile: File)(implicit encoder: Encoder[T], decoder: Decoder[T])
    extends DataStorage[T] {
  private val logger: Logger = getLogger

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
    logger.debug("Saving new values to storage")
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
