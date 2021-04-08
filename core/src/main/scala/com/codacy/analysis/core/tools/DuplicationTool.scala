package com.codacy.analysis.core.tools

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.core.model.DuplicationClone
import com.codacy.plugins.api.duplication.DuplicationTool.CodacyConfiguration
import com.codacy.plugins.api.languages.Language
import com.codacy.plugins.api
import com.codacy.plugins.duplication.traits
import com.codacy.plugins.duplication.utils.DuplicationTools
import com.codacy.plugins.runners.{BinaryDockerRunner, DockerRunner}
import org.log4s.getLogger

import scala.concurrent.duration._
import scala.util.Try

class DuplicationTool(private val duplicationTool: traits.DuplicationTool, val languageToRun: Language) extends ITool {

  override def name: String = "duplication"
  override def supportedLanguages: Set[Language] = duplicationTool.languages.to[Set]

  def run(directory: File,
          files: Set[Path],
          tmpDirectory: Option[File] = None,
          timeout: Option[Duration] = Option.empty[Duration],
          maxToolMemory: Option[String] = None): Try[Set[DuplicationClone]] = {

    val dockerRunner = new BinaryDockerRunner[api.duplication.DuplicationClone](
      duplicationTool,
      BinaryDockerRunner.Config(containerMemoryLimit = maxToolMemory))
    val runner = new traits.DuplicationRunner(duplicationTool, dockerRunner)

    for {
      duplicationClones <- runner.run(
        directory.toJava,
        CodacyConfiguration(Option(languageToRun), Option.empty),
        timeout.getOrElse(DockerRunner.defaultRunTimeout),
        dockerConfig = None,
        configTmpDirectory = tmpDirectory.map(_.toJava))
      clones = filterDuplicationClones(duplicationClones, files)
    } yield {
      clones.map(clone => DuplicationClone(clone.cloneLines, clone.nrTokens, clone.nrLines, clone.files.to[Set]))(
        collection.breakOut): Set[DuplicationClone]
    }
  }

  private def filterDuplicationClones(duplicationClones: List[api.duplication.DuplicationClone],
                                      files: Set[Path],
                                      minCloneLines: Int = 5): List[api.duplication.DuplicationClone] = {
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

  private def filterUnignoredFiles(duplicated: Seq[api.duplication.DuplicationCloneFile],
                                   expectedFiles: Set[String]): Seq[api.duplication.DuplicationCloneFile] = {
    duplicated.collect {
      case cloneFile if expectedFiles.contains(cloneFile.filePath) =>
        cloneFile
    }
  }
}

object DuplicationToolCollector {

  private val logger: org.log4s.Logger = getLogger

  private val availableTools: List[traits.DuplicationTool] = DuplicationTools.list

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
