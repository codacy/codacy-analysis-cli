package com.codacy.analysis.cli.files

import better.files.File
import cats.Foldable
import cats.implicits._
import com.codacy.analysis.cli.clients.api.{FilePath, ProjectConfiguration}
import com.codacy.analysis.cli.configuration.CodacyConfigurationFile
import com.codacy.analysis.cli.tools.Tool
import com.codacy.api.dtos.{Language, Languages}

import scala.util.Try

final case class FilesTarget(directory: File, files: Set[File], configFiles: Set[File])

class FileSystemFileCollector extends FileCollector[Try] {

  // TODO: Check if File will work or if we might need Path to support relative paths
  // TODO: Use remote configuration

  // HACK: Fixes Intellij IDEA highlight problems
  private type EitherA[A] = Either[String, A]
  private val foldable: Foldable[EitherA] = implicitly[Foldable[EitherA]]

  override def list(tool: Tool,
                    directory: File,
                    localConfiguration: Either[String, CodacyConfigurationFile],
                    remoteConfiguration: Either[String, ProjectConfiguration]): Try[FilesTarget] = {
    Try {
      val allFiles = directory.listRecursively().to[Set].filter(_.isRegularFile)

      val configFiles = allFiles.filter(f => tool.configFilenames.exists(cf => f.pathAsString.endsWith(cf)))

      val filters = Set(excludeGlobal(localConfiguration) _, excludePrefixes(remoteConfiguration) _)
      val filteredFiles = filters.foldLeft(allFiles) { case (fs, filter) => filter(fs) }

      FilesTarget(directory, filteredFiles, configFiles)
    }
  }

  override def filter(tool: Tool,
                      target: FilesTarget,
                      localConfiguration: Either[String, CodacyConfigurationFile],
                      remoteConfiguration: Either[String, ProjectConfiguration]): Try[FilesTarget] = {
    Try {
      val filters = Set(
        excludeForTool(tool, localConfiguration) _,
        filterByLanguage(tool, localConfiguration, remoteConfiguration) _)
      val filteredFiles = filters.foldLeft(target.files) { case (fs, filter) => filter(fs) }
      target.copy(files = filteredFiles)
    }
  }

  private def excludePrefixes(remoteConfiguration: Either[String, ProjectConfiguration])(
    files: Set[File]): Set[File] = {
    filterByPaths(files, foldable.foldMap(remoteConfiguration)(_.ignoredPaths))
  }

  private def excludeGlobal(localConfiguration: Either[String, CodacyConfigurationFile])(
    files: Set[File]): Set[File] = {
    val excludeGlobs = foldable.foldMap(localConfiguration)(_.exclude_paths.getOrElse(Set.empty[Glob]))
    filterByGlobs(files, excludeGlobs)
  }

  private def excludeForTool(tool: Tool, localConfiguration: Either[String, CodacyConfigurationFile])(
    files: Set[File]): Set[File] = {
    val excludeGlobs = foldable.foldMap(localConfiguration)(localConfig =>
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

  private def filterByPaths(files: Set[File], ignoredPaths: Set[FilePath]): Set[File] = {
    if (ignoredPaths.nonEmpty) {
      files.filterNot(file => ignoredPaths.exists(ip => file.pathAsString.startsWith(ip.value)))
    } else {
      files
    }
  }

  private def filterByLanguage(
    tool: Tool,
    localConfiguration: Either[String, CodacyConfigurationFile],
    remoteConfiguration: Either[String, ProjectConfiguration])(files: Set[File]): Set[File] = {
    val localCustomExtensionsByLanguage =
      localConfiguration.fold(_ => Map.empty[Language, Set[String]], localConfig => {
        localConfig.languages.fold(Map.empty[Language, Set[String]])(_.map {
          case (lang, config) => (lang, config.extensions.getOrElse(Set.empty[String]))
        }(collection.breakOut): Map[Language, Set[String]])
      })

    val remoteCustomExtensionsByLanguage: Map[Language, Set[String]] =
      foldable.foldMap(remoteConfiguration)(
        _.projectExtensions.map(le => (le.language, le.extensions))(collection.breakOut))

    Languages
      .filter(
        files.map(_.pathAsString),
        tool.languages,
        localCustomExtensionsByLanguage ++ remoteCustomExtensionsByLanguage)
      .map(File(_))
  }

}

object FileSystemFileCollector extends FileCollectorCompanion[Try] {

  val name: String = "file-system"

  override def apply(): FileCollector[Try] = new FileSystemFileCollector()

}
