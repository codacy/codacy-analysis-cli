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

  val storageFile: File = cacheFolder / storageFilename

  private def cacheFolder: File = {
    val defaultFolder = File.currentWorkingDirectory / ".codacy" / "codacy-analysis-cli"
    val cacheBaseFolderOpt = sys.props.get("user.home").map(File(_))

    cacheBaseFolderOpt.fold(defaultFolder) { cacheBaseFolder =>
      val osNameOpt = sys.props.get("os.name").map(_.toLowerCase)
      osNameOpt match {
        case Some(sysName) if sysName.contains("mac") =>
          cacheBaseFolder / "Library" / "Caches" / "com.codacy" / "codacy-analysis-cli"

        case Some(sysName) if sysName.contains("nix") || sysName.contains("nux") || sysName.contains("aix") =>
          cacheBaseFolder / ".cache" / "codacy" / "codacy-analysis-cli"

        case Some(sysName) if sysName.contains("windows") =>
          val windowsCacheDir = File(sys.env("APPDATA"))
          if (windowsCacheDir.exists) {
            windowsCacheDir / "Codacy" / "codacy-analysis-cli"
          } else {
            defaultFolder
          }
        case _ => defaultFolder
      }
    }
  }

  private def writeToFile(file: File, content: String): Try[File] =
    Try {
      file.createFileIfNotExists(createParents = true)
      file.write(content)
    }

  private def readFromFile(file: File): Try[String] =
    Try {
      file.contentAsString
    }

  def invalidate(): Try[Unit] =
    Try {
      logger.debug("Invalidating storage")
      storageFile.delete()
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
      val fileContentOpt = readFromFile(storageFile).toOption
      fileContentOpt.flatMap { content =>
        parser.decode[Seq[T]](content).fold(_ => None, v => Some(v)).filter(_.nonEmpty)
      }
    } else {
      None
    }
  }
}
