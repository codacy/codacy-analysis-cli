package com.codacy.analysis.cli.files

import better.files.File
import com.codacy.analysis.cli.configuration.CodacyConfigurationFile
import com.codacy.analysis.cli.tools.Tool
import com.codacy.analysis.cli.utils.Glob
import com.codacy.api.dtos.{Language, Languages}

import scala.util.Try

final case class FilesTarget(directory: File, files: Set[File])

class FileSystemFileCollector extends FileCollector[Try] {

  // TODO: Check if File will work or if we might need Path to support relative paths

  /* TODO:
   *   Check if we can optimize the number of times we list files:
   *     - List and pass through global excludes
   *     - Then pass the files to each tool:
   *       + Apply tool excludes
   *       + Filter by language extensions
   */

  override def list(tool: Tool,
                    directory: File,
                    localConfiguration: Either[String, CodacyConfigurationFile],
                    remoteConfiguration: AnyRef): Try[FilesTarget] = {
    Try {
      val allFiles = if (directory.isDirectory) {
        directory.listRecursively().to[Set].filter(_.isRegularFile)
      } else {
        Set(directory)
      }

      val filesWithoutExcluded = excludeByGlobs(tool, localConfiguration, allFiles)
      val filesFilteredByLanguages = filterByLanguage(tool, localConfiguration, filesWithoutExcluded)

      FilesTarget(directory, filesFilteredByLanguages)
    }
  }

  private def excludeByGlobs(tool: Tool,
                             localConfiguration: Either[String, CodacyConfigurationFile],
                             files: Set[File]): Set[File] = {
    val excludeGlobs = localConfiguration.fold(
      _ => Set.empty[Glob],
      localConfig => {
        val globalExcludes = localConfig.exclude_paths.getOrElse(Set.empty[Glob])
        val toolExcludes = localConfig.engines.fold(Set.empty[Glob])(
          _.get(tool.name).fold(Set.empty[Glob])(_.exclude_paths.getOrElse(Set.empty[Glob])))
        globalExcludes ++ toolExcludes
      })

    if (excludeGlobs.nonEmpty) {
      files.filterNot(file => excludeGlobs.exists(_.matches(file.pathAsString)))
    } else {
      files
    }
  }

  private def filterByLanguage(tool: Tool,
                               localConfiguration: Either[String, CodacyConfigurationFile],
                               files: Set[File]): Set[File] = {
    val customExtensionsByLanguage = localConfiguration.fold(_ => Map.empty[Language, Set[String]], localConfig => {
      localConfig.languages.fold(Map.empty[Language, Set[String]])(_.map {
        case (lang, config) => (lang, config.extensions.getOrElse(Set.empty[String]))
      }(collection.breakOut): Map[Language, Set[String]])
    })

    val extensions = tool.languages.flatMap { l =>
      customExtensionsByLanguage.getOrElse(l, Languages.extensionsByLanguage.getOrElse(l, Set.empty))
    }

    files.filter { file =>
      extensions.exists(e => file.pathAsString.endsWith(e))
    }
  }

}

object FileSystemFileCollector extends FileCollectorCompanion[Try] {

  val name: String = "file-system"

  override def apply(): FileCollector[Try] = new FileSystemFileCollector()

}
