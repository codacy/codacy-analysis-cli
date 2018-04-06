package com.codacy.analysis.cli.files

import better.files.File
import com.codacy.analysis.cli.configuration.CodacyConfigurationFile
import com.codacy.analysis.cli.tools.Tool
import org.log4s.Logger

import scala.util.Try

trait FileCollectorCompanion[T[_]] {
  def name: String
  def apply(): FileCollector[T]
}

trait FileCollector[T[_]] {

  def list(directory: File,
           localConfiguration: Either[String, CodacyConfigurationFile],
           remoteConfiguration: AnyRef): T[FilesTarget]

  def filter(tool: Tool,
             target: FilesTarget,
             localConfiguration: Either[String, CodacyConfigurationFile],
             remoteConfiguration: AnyRef): T[FilesTarget]

}

object FileCollector {

  val defaultCollector: FileCollectorCompanion[Try] = FileSystemFileCollector

  val allCollectors: Set[FileCollectorCompanion[Try]] = Set(defaultCollector)

  def apply(name: String)(implicit logger: Logger): FileCollector[Try] = {
    val builder = allCollectors.find(_.name.equalsIgnoreCase(name)).getOrElse {
      logger.warn(s"Could not find file collector for name $name. Using ${defaultCollector.name} as fallback.")
      defaultCollector
    }

    builder()
  }
}
