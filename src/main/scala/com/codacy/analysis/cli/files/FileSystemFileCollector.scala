package com.codacy.analysis.cli.files

import java.nio.file.{Path, Paths}

import better.files.File
import cats.Foldable
import cats.implicits._
import com.codacy.analysis.cli.clients.api.{FilePath, PathRegex, ProjectConfiguration}
import com.codacy.analysis.cli.configuration.CodacyConfigurationFile
import com.codacy.analysis.cli.tools.Tool
import com.codacy.plugins.api.languages.{Language, Languages}

import scala.util.Try

final case class FilesTarget(directory: File, files: Set[Path])

class FileSystemFileCollector extends FileCollector[Try] {

  // TODO: Check if File will work or if we might need Path to support relative paths

  // HACK: Fixes Intellij IDEA highlight problems
  private type EitherA[A] = Either[String, A]
  private val foldable: Foldable[EitherA] = implicitly[Foldable[EitherA]]

  override def list(directory: File,
                    localConfiguration: Either[String, CodacyConfigurationFile],
                    remoteConfiguration: Either[String, ProjectConfiguration]): Try[FilesTarget] = {
    Try {
      val allFiles =
        directory
          .listRecursively()
          .collect { case f if f.isRegularFile => directory.relativize(f) }
          .filterNot(_.startsWith(".git"))
          .to[Set]

      val autoIgnoresFilter: Set[Path] => Set[Path] = if (localConfiguration.isLeft) {
        excludeAutoIgnores(remoteConfiguration)
      } else {
        identity
      }

      val filters: Set[Set[Path] => Set[Path]] =
        Set(excludeGlobal(localConfiguration)(_), excludePrefixes(remoteConfiguration)(_), autoIgnoresFilter(_))

      val filteredFiles = filters.foldLeft(allFiles) { case (fs, filter) => filter(fs) }

      FilesTarget(directory, filteredFiles)
    }
  }

  override def hasConfigurationFiles(tool: Tool, filesTarget: FilesTarget): Boolean = {
    filesTarget.files.exists(f => tool.configFilenames.exists(cf => f.endsWith(cf)))
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
    files: Set[Path]): Set[Path] = {
    filterByPaths(files, foldable.foldMap(remoteConfiguration)(_.ignoredPaths))
  }

  private def excludeGlobal(localConfiguration: Either[String, CodacyConfigurationFile])(
    files: Set[Path]): Set[Path] = {
    val excludeGlobs = foldable.foldMap(localConfiguration)(_.exclude_paths.getOrElse(Set.empty[Glob]))
    filterByGlobs(files, excludeGlobs)
  }

  private def excludeForTool(tool: Tool, localConfiguration: Either[String, CodacyConfigurationFile])(
    files: Set[Path]): Set[Path] = {
    val excludeGlobs = foldable.foldMap(localConfiguration)(localConfig =>
      localConfig.engines.foldMap(_.get(tool.name).foldMap(_.exclude_paths.getOrElse(Set.empty[Glob]))))
    filterByGlobs(files, excludeGlobs)
  }

  private def excludeAutoIgnores(remoteConfiguration: Either[String, ProjectConfiguration])(
    files: Set[Path]): Set[Path] = {
    val excludeIgnores: Set[PathRegex] = foldable.foldMap(remoteConfiguration)(_.defaultIgnores.getOrElse(Set.empty))
    filterByExpression(files, excludeIgnores)
  }

  private def filterByGlobs(files: Set[Path], excludeGlobs: Set[Glob]): Set[Path] = {
    if (excludeGlobs.nonEmpty) {
      files.filterNot(file => excludeGlobs.exists(_.matches(file.toString)))
    } else {
      files
    }
  }

  private def filterByExpression(files: Set[Path], regexExcludes: Set[PathRegex]): Set[Path] = {
    if (regexExcludes.nonEmpty) {
      files.filterNot(file => regexExcludes.exists(regex => file.toString.matches(regex.value)))
    } else {
      files
    }
  }

  private def filterByPaths(files: Set[Path], ignoredPaths: Set[FilePath]): Set[Path] = {
    if (ignoredPaths.nonEmpty) {
      files.filterNot(file => ignoredPaths.exists(ip => file.startsWith(ip.value)))
    } else {
      files
    }
  }

  private def filterByLanguage(
    tool: Tool,
    localConfiguration: Either[String, CodacyConfigurationFile],
    remoteConfiguration: Either[String, ProjectConfiguration])(files: Set[Path]): Set[Path] = {

    val localCustomExtensionsByLanguage =
      localConfiguration.map(_.languageCustomExtensions).getOrElse(Map.empty)

    val remoteCustomExtensionsByLanguage: Map[Language, Set[String]] =
      foldable.foldMap(remoteConfiguration)(
        _.projectExtensions.map(le => (le.language, le.extensions))(collection.breakOut))

    Languages
      .filter(
        files.map(_.toString),
        tool.languages,
        localCustomExtensionsByLanguage ++ remoteCustomExtensionsByLanguage)
      .map(Paths.get(_))
  }

}

object FileSystemFileCollector extends FileCollectorCompanion[Try] {

  val name: String = "file-system"

  override def apply(): FileCollector[Try] = new FileSystemFileCollector()

}
