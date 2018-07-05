package com.codacy.analysis.core.tools

import better.files.File
import com.codacy.analysis.core.files.FilesTarget
import com.codacy.analysis.core.model.{DuplicationClone}
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.duplication._
import com.codacy.plugins.duplication.api.{DuplicationCloneFile, DuplicationConfiguration, DuplicationRequest}
import com.codacy.plugins.duplication.traits.DuplicationRunner
import com.codacy.plugins.utils.PluginHelper
import org.log4s.getLogger

import scala.concurrent.duration._
import scala.util.Try

class DuplicationTool(private val duplicationTool: traits.DuplicationTool, val language: Language) extends ITool {

  override def name: String = "duplication"
  override def supportedLanguages: Set[Language] = duplicationTool.languages.to[Set]

  def run(directory: File, filesTarget: FilesTarget, timeout: Duration = 10.minutes): Try[Set[DuplicationClone]] = {

    val request = DuplicationRequest(directory.pathAsString)

    DuplicationRunner(duplicationTool)
      .run(request, DuplicationConfiguration(language, Map.empty), Some(timeout), None)
      .map(
        clones =>
          filterDuplicationClones(clones, filesTarget).map(
            clone => DuplicationClone(clone.cloneLines, clone.nrTokens, clone.nrLines, clone.files))(
            collection.breakOut): Set[DuplicationClone])
  }

  private def filterDuplicationClones(duplicationClones: List[api.DuplicationClone],
                                      filesTarget: FilesTarget,
                                      minCloneLines: Int = 5) = {
    // The duplication files should be more than 1. If it is one, then it means
    // that the other clone was in an ignored file. This is based on the assumption
    // that the duplication results will contain more than one entry for files
    // with duplicated clones with themselves.
    duplicationClones.collect {
      case clone if clone.nrLines >= minCloneLines =>
        val commitFileNames = filesTarget.readableFiles.map(_.toString)
        val filteredFiles = filterUnignoredFiles(clone.files, commitFileNames)
        (clone.copy(files = filteredFiles), filteredFiles.length)
    }.collect { case (clone, nrCloneFiles) if nrCloneFiles > 1 => clone }
  }

  private def filterUnignoredFiles(duplicated: Seq[DuplicationCloneFile], expectedFiles: Set[String]) = {
    duplicated.collect {
      case cloneFile if expectedFiles.contains(cloneFile.filePath) =>
        cloneFile
    }
  }
}

object DuplicationToolCollector {

  private val logger: org.log4s.Logger = getLogger

  private val availableTools = PluginHelper.dockerDuplicationPlugins

  def fromLanguage(languageName: String): Either[String, Set[DuplicationTool]] = {
    val collectedTools: Set[DuplicationTool] = (for {
      tool <- availableTools.find(p => p.languages.exists(_.name.equalsIgnoreCase(languageName))).to[List]
      language <- tool.languages
    } yield {
      new DuplicationTool(tool, language)
    })(collection.breakOut)

    if (collectedTools.isEmpty) {
      logger.info(s"No duplication tools found for the language $languageName")
    }

    Right(collectedTools)
  }

  def fromFileTarget(filesTarget: FilesTarget,
                     languageCustomExtensions: List[(Language, Seq[String])]): Either[String, Set[DuplicationTool]] = {

    val languages = for {
      path <- filesTarget.readableFiles
      language <- Languages.forPath(path.toString, languageCustomExtensions)
    } yield language

    val duplicationTools = languages.flatMap { lang =>
      availableTools.collect {
        case tool if tool.languages.contains(lang) =>
          new DuplicationTool(tool, lang)
      }
    }

    if (duplicationTools.isEmpty) {
      logger.info("No duplication tools found for the provided files.")
    }

    Right(duplicationTools)
  }

}
