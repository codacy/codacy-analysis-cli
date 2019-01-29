package com.codacy.analysis.core.tools

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.core.model.DuplicationClone
import com.codacy.plugins.api.duplication.DuplicationTool.CodacyConfiguration
import com.codacy.plugins.api.languages.Language
import com.codacy.plugins.duplication.api.{DuplicationCloneFile, DuplicationRequest}
import com.codacy.plugins.duplication.traits.DuplicationRunner
import com.codacy.plugins.duplication.{api, _}
import com.codacy.plugins.traits.{BinaryDockerRunner, DockerRunner}
import com.codacy.plugins.utils.PluginHelper
import org.log4s.getLogger

import scala.concurrent.duration._
import scala.util.Try

class DuplicationTool(private val duplicationTool: traits.DuplicationTool, val languageToRun: Language) extends ITool {

  override def name: String = "duplication"
  override def supportedLanguages: Set[Language] = duplicationTool.languages.to[Set]

  def run(directory: File,
          files: Set[Path],
          timeout: Option[Duration] = Option.empty[Duration]): Try[Set[DuplicationClone]] = {

    val request = DuplicationRequest(directory.pathAsString)

    val dockerRunner = new BinaryDockerRunner[api.DuplicationClone](duplicationTool)()
    val runner = new DuplicationRunner(duplicationTool, dockerRunner)

    for {
      duplicationClones <- runner.run(
        request,
        CodacyConfiguration(Option(languageToRun), Option.empty),
        timeout.getOrElse(DockerRunner.defaultRunTimeout),
        None)
      clones = filterDuplicationClones(duplicationClones, files)
    } yield {
      clones.map(clone => DuplicationClone(clone.cloneLines, clone.nrTokens, clone.nrLines, clone.files.to[Set]))(
        collection.breakOut): Set[DuplicationClone]
    }
  }

  private def filterDuplicationClones(duplicationClones: List[api.DuplicationClone],
                                      files: Set[Path],
                                      minCloneLines: Int = 5): List[api.DuplicationClone] = {
    // The duplication files should be more than 1. If it is one, then it means
    // that the other clone was in an ignored file. This is based on the assumption
    // that the duplication results will contain more than one entry for files
    // with duplicated clones with themselves.
    duplicationClones.collect {
      case clone if clone.nrLines >= minCloneLines =>
        val commitFileNames = files.map(_.toString)
        val filteredFiles = filterUnignoredFiles(clone.files, commitFileNames)
        (clone.copy(files = filteredFiles), filteredFiles.length)
    }.collect { case (clone, nrCloneFiles) if nrCloneFiles > 1 => clone }
  }

  private def filterUnignoredFiles(duplicated: Seq[DuplicationCloneFile],
                                   expectedFiles: Set[String]): Seq[DuplicationCloneFile] = {
    duplicated.collect {
      case cloneFile if expectedFiles.contains(cloneFile.filePath) =>
        cloneFile
    }
  }
}

object DuplicationToolCollector {

  private val logger: org.log4s.Logger = getLogger

  private val availableTools: List[traits.DuplicationTool] = PluginHelper.dockerDuplicationPlugins

  def fromLanguages(languages: Set[Language]): Set[DuplicationTool] = {
    languages.flatMap { lang =>
      val collectedTools = availableTools.collect {
        case tool if tool.languages.contains(lang) =>
          new DuplicationTool(tool, lang)
      }
      if (collectedTools.isEmpty) {
        logger.info(s"No duplication tools found for language ${lang.name}")
      }
      collectedTools
    }
  }

}
