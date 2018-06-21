package com.codacy.analysis.core.files

import better.files.File
import com.codacy.analysis.core.clients.api.ProjectConfiguration
import com.codacy.analysis.core.configuration.CodacyConfigurationFile

import scala.util.Try

class FallbackFileCollector(fileCollectorCompanions: List[FileCollectorCompanion[Try]]) extends FileCollector[Try] {
  private val fileCollectors = fileCollectorCompanions.map(_.apply())

  override def list(directory: File,
                    localConfiguration: Either[String, CodacyConfigurationFile],
                    remoteConfiguration: Either[String, ProjectConfiguration]): Try[FilesTarget] = {
    list(fileCollectors, directory, localConfiguration, remoteConfiguration)
  }

  private def list(fileCollectorList: List[FileCollector[Try]],
                   directory: File,
                   localConfiguration: Either[String, CodacyConfigurationFile],
                   remoteConfiguration: Either[String, ProjectConfiguration]): Try[FilesTarget] = {
    fileCollectorList match {
      case fileCollector :: tail =>
        fileCollector.list(directory, localConfiguration, remoteConfiguration).recoverWith {
          case _ =>
            list(tail, directory, localConfiguration, remoteConfiguration)
        }
      case Nil =>
        scala.util.Failure(
          new Exception(s"All FileCollectors failed: ${fileCollectorCompanions.map(_.name).mkString(",")}"))
    }
  }
}

class FallbackFileCollectorCompanion(fileCollectorCompanions: List[FileCollectorCompanion[Try]])
    extends FileCollectorCompanion[Try] {

  val name: String = s"fallback:${fileCollectorCompanions.map(_.name).mkString(",")}"

  override def apply(): FileCollector[Try] = new FallbackFileCollector(fileCollectorCompanions)

}
