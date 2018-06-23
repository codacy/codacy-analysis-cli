package com.codacy.analysis.core.files

import better.files.File
import com.codacy.analysis.core.clients.api.ProjectConfiguration
import com.codacy.analysis.core.configuration.CodacyConfigurationFile
import com.codacy.analysis.core.tools.Tool
import org.log4s.{Logger, getLogger}

import scala.util.Try

trait FileCollectorCompanion[T[_]] {
  def name: String
  def apply(): FileCollector[T]
}

trait FileCollector[T[_]] {

  def list(directory: File,
           localConfiguration: Either[String, CodacyConfigurationFile],
           remoteConfiguration: Either[String, ProjectConfiguration]): T[FilesTarget]

  def filter(tool: Tool,
             target: FilesTarget,
             localConfiguration: Either[String, CodacyConfigurationFile],
             remoteConfiguration: Either[String, ProjectConfiguration]): T[FilesTarget]

  def hasConfigurationFiles(tool: Tool, filesTarget: FilesTarget): Boolean

}

object FileCollector {

  private val logger: Logger = getLogger

  val defaultCollector: FileCollectorCompanion[Try] = FileSystemFileCollector

  val allCollectors: Set[FileCollectorCompanion[Try]] = Set(defaultCollector)

  def apply(name: String): FileCollector[Try] = {
    val builder = allCollectors.find(_.name.equalsIgnoreCase(name)).getOrElse {
      logger.warn(s"Could not find file collector for name $name. Using ${defaultCollector.name} as fallback.")
      defaultCollector
    }

    builder()
  }
}
