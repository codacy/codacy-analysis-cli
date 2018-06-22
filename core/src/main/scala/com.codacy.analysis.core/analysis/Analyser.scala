package com.codacy.analysis.core.analysis

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.core.model.{Configuration, Result}
import com.codacy.analysis.core.tools.Tool
import org.log4s.{Logger, getLogger}

import scala.util.Try

trait AnalyserCompanion[T[_]] {
  def name: String
  def apply(): Analyser[T]
}

trait Analyser[T[_]] {

  def analyse(tool: Tool, directory: File, files: Set[Path], config: Configuration): T[Set[Result]]

}

object Analyser {

  private val logger: Logger = getLogger

  val defaultAnalyser: AnalyserCompanion[Try] = CodacyPluginsAnalyser

  val allAnalysers: Set[AnalyserCompanion[Try]] = Set(defaultAnalyser)

  def apply(name: String): Analyser[Try] = {
    val builder = allAnalysers.find(_.name.equalsIgnoreCase(name)).getOrElse {
      logger.warn(s"Could not find analyser for name $name. Using ${defaultAnalyser.name} as fallback.")
      defaultAnalyser
    }

    builder()
  }
}
