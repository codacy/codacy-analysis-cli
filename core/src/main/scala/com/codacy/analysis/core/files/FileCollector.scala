package com.codacy.analysis.core.files

import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Path, Paths}

import better.files.File
import cats.instances.try_.catsStdInstancesForTry
import com.codacy.analysis.core.clients.api.{FilePath, PathRegex}
import com.codacy.analysis.core.tools.{ITool, Tool}
import com.codacy.plugins.api.languages.Language
import org.log4s.{Logger, getLogger}

import scala.util.Try

trait FileCollectorCompanion[T[_]] {
  def name: String
  def apply(): FileCollector[T]
}

final case class FileExclusionRules(defaultIgnores: Option[Set[PathRegex]],
                                    ignoredPaths: Set[FilePath],
                                    excludePaths: ExcludePaths,
                                    allowedExtensionsByLanguage: Map[Language, Set[String]])
final case class ExcludePaths(global: Set[Glob], byTool: Map[String, Set[Glob]])

trait FileCollector[T[_]] {

  protected val logger: Logger = getLogger

  def list(directory: File): T[FilesTarget]

  def filterGlobal(target: FilesTarget, exclusionRules: FileExclusionRules): FilesTarget = {

    val autoIgnoresFilter: Set[Path] => Set[Path] =
      exclusionRules.defaultIgnores.fold(identity[Set[Path]] _)(autoIgnores => filterByExpression(_, autoIgnores))

    val filters: Set[Set[Path] => Set[Path]] =
      Set(
        filterByGlobs(_, exclusionRules.excludePaths.global),
        filterByPaths(_, exclusionRules.ignoredPaths),
        autoIgnoresFilter)

    val filteredFiles = filters.foldLeft(target.readableFiles) { case (fs, filter) => filter(fs) }

    target.copy(readableFiles = filteredFiles)
  }

  def hasConfigurationFiles(tool: Tool, filesTarget: FilesTarget): Boolean = {
    filesTarget.readableFiles.exists(f => tool.configFilenames.exists(cf => f.endsWith(cf)))
  }

  def filterTool(tool: ITool, target: FilesTarget, exclusionRules: FileExclusionRules): FilesTarget = {

    val filters =
      Set[Set[Path] => Set[Path]](
        filterByGlobs(_, exclusionRules.excludePaths.byTool.getOrElse(tool.name, Set.empty)),
        filterByLanguage(tool.languageToRun, exclusionRules.allowedExtensionsByLanguage))

    val filteredFiles = filters.foldLeft(target.readableFiles) { case (fs, filter) => filter(fs) }

    target.copy(readableFiles = filteredFiles)
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

  private def filterByLanguage(language: Language, extensionsByLanguage: Map[Language, Set[String]])(
    files: Set[Path]): Set[Path] = {
    val allExtensions =
      extensionsByLanguage.getOrElse(language, Set.empty) ++
        language.extensions ++ language.files

    files.map(_.toString).filter(file => allExtensions.exists(e => file.endsWith(e))).map(Paths.get(_))
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

  val allCollectors: Set[FileCollectorCompanion[Try]] =
    Set(GitFileCollector, FileSystemFileCollector)

  def apply(name: String): FileCollector[Try] = {
    val builder = allCollectors.find(_.name.equalsIgnoreCase(name)).getOrElse {
      logger.warn(s"Could not find file collector for name $name. Using ${defaultCollector.name} as fallback.")
      defaultCollector
    }

    builder()
  }
}
