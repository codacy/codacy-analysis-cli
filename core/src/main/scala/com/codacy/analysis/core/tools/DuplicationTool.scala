package com.codacy.analysis.core.tools

import better.files.File
import com.codacy.analysis.core.files.FilesTarget
import com.codacy.analysis.core.model.{DuplicationClone, Result}
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.duplication._
import com.codacy.plugins.duplication.api.{DuplicationCloneFile, DuplicationConfiguration, DuplicationRequest}
import com.codacy.plugins.duplication.traits.DuplicationRunner
import com.codacy.plugins.utils.PluginHelper

import scala.concurrent.duration._
import scala.util.Try

class DuplicationTool(private val duplicationTool: traits.DuplicationTool, val language: Language) extends ITool {

  override def name: String = "duplication"
  override def supportedLanguages: Set[Language] = duplicationTool.languages.to[Set]

  def run(directory: File,
          filesTarget: FilesTarget,
          minCloneLines: Int,
          timeout: Duration = 10.minutes): Try[Set[Result]] = {

    val request = DuplicationRequest(directory.pathAsString)

    // The duplication files should be more than 1. If it is one, then it means
    // that the other clone was in an ignored file. This is based on the assumption
    // that the duplication results will contain more than one entry for files
    // with duplicated clones with themselves.
    DuplicationRunner(duplicationTool)
      .run(request, DuplicationConfiguration(language, Map.empty), Some(timeout), None)
      .map(clones =>
        filterDuplicationClones(clones, filesTarget, minCloneLines).map(clone =>
          DuplicationClone(clone.nrTokens, clone.nrLines, clone.files))(collection.breakOut): Set[Result])
  }

  private def filterDuplicationClones(duplicationClones: List[api.DuplicationClone],
                                      filesTarget: FilesTarget,
                                      minCloneLines: Int) = {
    duplicationClones.collect {
      case clone if clone.nrLines >= minCloneLines =>
        val commitFileNames = filesTarget.readableFiles.map(_.toString)
        val filteredFiles = filterUnignoredFilesFromDuplication(clone.files, commitFileNames)
        (clone.copy(files = filteredFiles), filteredFiles.length)
    }.collect { case (clone, nrCloneFiles) if nrCloneFiles > 1 => clone }
  }

  private def filterUnignoredFilesFromDuplication(duplicated: Seq[DuplicationCloneFile], expectedFiles: Set[String]) = {
    duplicated.collect {
      case cloneFile if expectedFiles.contains(cloneFile.filePath) =>
        cloneFile
    }
  }
}

object DuplicationToolCollector {

  private val availableTools = PluginHelper.dockerDuplicationPlugins

  def fromNameOrUUID(toolInput: String): Either[String, Set[DuplicationTool]] = {
    val collectedToolsWithPaths: Set[DuplicationTool] = (for {
      tool <- availableTools
        .find(p => p.dockerName.equalsIgnoreCase(toolInput) || p.dockerImageName.equalsIgnoreCase(toolInput))
        .to[List]
      language <- tool.languages
    } yield {
      new DuplicationTool(tool, language)
    })(collection.breakOut)

    if (collectedToolsWithPaths.isEmpty) {
      Left("No tools found for the provided input")
    } else {
      Right(collectedToolsWithPaths)
    }
  }

  def fromFileTarget(filesTarget: FilesTarget,
                     languageCustomExtensions: List[(Language, Seq[String])]): Either[String, Set[DuplicationTool]] = {

    val collectedTools: Set[DuplicationTool] = (for {
      path <- filesTarget.readableFiles
      lang <- Languages.forPath(path.toString, languageCustomExtensions)
    } yield {
      availableTools.collect {
        case tool if tool.languages.contains(lang) =>
          (tool, lang)
      }
    }).flatten.map {
      case (tool, lang) => new DuplicationTool(tool, lang)
    }

    if (collectedTools.isEmpty) {
      Left("No tools found for files provided")
    } else {
      Right(collectedTools)
    }
  }

}
