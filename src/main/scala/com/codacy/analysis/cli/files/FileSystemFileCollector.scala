package com.codacy.analysis.cli.files

import better.files.File
import cats.implicits._
import com.codacy.analysis.cli.configuration.CodacyConfigurationFile
import com.codacy.analysis.cli.tools.Tool
import com.codacy.analysis.cli.utils.Glob
import com.codacy.api.dtos.{Language, Languages}

import scala.util.Try

final case class FilesTarget(directory: File, files: Set[File])

class FileSystemFileCollector extends FileCollector[Try] {

  // TODO: Check if File will work or if we might need Path to support relative paths
  // TODO: Use remote configuration

  override def list(directory: File,
                    localConfiguration: Either[String, CodacyConfigurationFile],
                    remoteConfiguration: AnyRef): Try[FilesTarget] = {
    Try {
      val allFiles = directory.listRecursively().to[Set].filter(_.isRegularFile)
      val filteredFiles = excludeGlobal(localConfiguration)(allFiles)
      FilesTarget(directory, filteredFiles)
    }
  }

  override def filter(tool: Tool,
                      target: FilesTarget,
                      localConfiguration: Either[String, CodacyConfigurationFile],
                      remoteConfiguration: AnyRef): Try[FilesTarget] = {
    Try {
      val filters = Set(excludeForTool(tool, localConfiguration) _, filterByLanguage(tool, localConfiguration) _)
      val filteredFiles = filters.foldLeft(target.files) { case (fs, filter) => filter(fs) }
      target.copy(files = filteredFiles)
    }
  }

  private def excludeGlobal(localConfiguration: Either[String, CodacyConfigurationFile])(
    files: Set[File]): Set[File] = {
    val excludeGlobs = localConfiguration.foldMap(_.exclude_paths.getOrElse(Set.empty[Glob]))
    filterByGlobs(files, excludeGlobs)
  }

  private def excludeForTool(tool: Tool, localConfiguration: Either[String, CodacyConfigurationFile])(
    files: Set[File]): Set[File] = {
    val excludeGlobs = localConfiguration.foldMap(localConfig =>
      localConfig.engines.foldMap(_.get(tool.name).foldMap(_.exclude_paths.getOrElse(Set.empty[Glob]))))
    filterByGlobs(files, excludeGlobs)
  }

  private def filterByGlobs(files: Set[File], excludeGlobs: Set[Glob]): Set[File] = {
    if (excludeGlobs.nonEmpty) {
      files.filterNot(file => excludeGlobs.exists(_.matches(file.pathAsString)))
    } else {
      files
    }
  }

  private def filterByLanguage(tool: Tool, localConfiguration: Either[String, CodacyConfigurationFile])(
    files: Set[File]): Set[File] = {
    val customExtensionsByLanguage = localConfiguration.fold(_ => Map.empty[Language, Set[String]], localConfig => {
      localConfig.languages.fold(Map.empty[Language, Set[String]])(_.map {
        case (lang, config) => (lang, config.extensions.getOrElse(Set.empty[String]))
      }(collection.breakOut): Map[Language, Set[String]])
    })

    Languages.filter(files.map(_.pathAsString), tool.languages, customExtensionsByLanguage).map(File(_))
  }

}

object FileSystemFileCollector extends FileCollectorCompanion[Try] {

  val name: String = "file-system"

  override def apply(): FileCollector[Try] = new FileSystemFileCollector()

}
