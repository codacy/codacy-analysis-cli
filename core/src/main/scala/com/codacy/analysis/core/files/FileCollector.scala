package com.codacy.analysis.core.files

import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Path, Paths}

import better.files.File
import cats.Foldable
import cats.implicits._
import com.codacy.analysis.core.clients.api.{FilePath, PathRegex, ProjectConfiguration}
import com.codacy.analysis.core.configuration.CodacyConfigurationFile
import com.codacy.analysis.core.tools.{ITool, Tool}
import com.codacy.plugins.api.languages.{Language, Languages}
import org.log4s.{Logger, getLogger}

import scala.util.Try

trait FileCollectorCompanion[T[_]] {
  def name: String
  def apply(): FileCollector[T]
}

trait FileCollector[T[_]] {

  // HACK: Fixes Intellij IDEA highlight problems
  private type EitherA[A] = Either[String, A]
  private val foldable: Foldable[EitherA] = implicitly[Foldable[EitherA]]

  protected val logger: Logger = getLogger

  def list(directory: File,
           localConfiguration: Either[String, CodacyConfigurationFile],
           remoteConfiguration: Either[String, ProjectConfiguration]): T[FilesTarget]

  protected def defaultFilter(allFiles: Set[Path],
                              localConfiguration: Either[String, CodacyConfigurationFile],
                              remoteConfiguration: Either[String, ProjectConfiguration]): Set[Path] = {

    val autoIgnoresFilter: Set[Path] => Set[Path] = if (localConfiguration.isLeft) {
      excludeAutoIgnores(remoteConfiguration)
    } else {
      identity
    }

    val filters: Set[Set[Path] => Set[Path]] =
      Set(excludeGlobal(localConfiguration)(_), excludePrefixes(remoteConfiguration)(_), autoIgnoresFilter(_))

    val filteredFiles = filters.foldLeft(allFiles) { case (fs, filter) => filter(fs) }

    filteredFiles
  }

  def hasConfigurationFiles(tool: Tool, filesTarget: FilesTarget): Boolean = {
    filesTarget.readableFiles.exists(f => tool.configFilenames.exists(cf => f.endsWith(cf)))
  }

  def filter(tool: ITool,
             target: FilesTarget,
             localConfiguration: Either[String, CodacyConfigurationFile],
             remoteConfiguration: Either[String, ProjectConfiguration]): FilesTarget = {

    val filters =
      Set(
        excludeForTool(tool.name, localConfiguration) _,
        filterByLanguage(tool.supportedLanguages, localConfiguration, remoteConfiguration) _)
    val filteredFiles = filters.foldLeft(target.readableFiles) { case (fs, filter) => filter(fs) }
    target.copy(readableFiles = filteredFiles)
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

  private def excludeForTool(toolName: String, localConfiguration: Either[String, CodacyConfigurationFile])(
    files: Set[Path]): Set[Path] = {
    val excludeGlobs = foldable.foldMap(localConfiguration)(localConfig =>
      localConfig.engines.foldMap(_.get(toolName).foldMap(_.exclude_paths.getOrElse(Set.empty[Glob]))))
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
    languages: Set[Language],
    localConfiguration: Either[String, CodacyConfigurationFile],
    remoteConfiguration: Either[String, ProjectConfiguration])(files: Set[Path]): Set[Path] = {

    val localCustomExtensionsByLanguage =
      localConfiguration.map(_.languageCustomExtensions).getOrElse(Map.empty)

    val remoteCustomExtensionsByLanguage: Map[Language, Set[String]] =
      foldable.foldMap(remoteConfiguration)(
        _.projectExtensions.map(le => (le.language, le.extensions))(collection.breakOut))

    Languages
      .filter(files.map(_.toString), languages, localCustomExtensionsByLanguage ++ remoteCustomExtensionsByLanguage)
      .map(Paths.get(_))
  }

  protected def checkPermissions(directory: File, files: Set[Path]): CheckedFiles = {
    files.map(path => (path, directory / path.toString)).foldLeft(CheckedFiles(Set.empty[Path], Set.empty[Path])) {
      case (checkedFiles, (path, file)) if file.isRegularFile && file.testPermission(PosixFilePermission.OTHERS_READ) =>
        checkedFiles.copy(readableFiles = checkedFiles.readableFiles + path)
      case (checkedFiles, (path, file)) =>
        logger.error(s"Could not read file $file, make sure it is readable by everybody.")
        checkedFiles.copy(unreadableFiles = checkedFiles.unreadableFiles + path)
    }
  }

}

object FileCollector {

  private val logger: Logger = getLogger

  val defaultCollector: FileCollectorCompanion[Try] = new FallbackFileCollectorCompanion(
    List(GitFileCollector, FileSystemFileCollector))

  val allCollectors: Set[FileCollectorCompanion[Try]] = Set(GitFileCollector, FileSystemFileCollector)

  def apply(name: String): FileCollector[Try] = {
    val builder = allCollectors.find(_.name.equalsIgnoreCase(name)).getOrElse {
      logger.warn(s"Could not find file collector for name $name. Using ${defaultCollector.name} as fallback.")
      defaultCollector
    }

    builder()
  }
}
