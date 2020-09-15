package com.codacy.analysis.cli.formatter

import java.io.{FileOutputStream, PrintStream}

import better.files.File
import com.codacy.analysis.core.model.Result
import com.codacy.plugins.api.PatternDescription
import org.log4s.{Logger, getLogger}

trait FormatterCompanion {
  def name: String
  def apply(outputStream: PrintStream, executionDirectory: File): Formatter
}

trait Formatter {

  def stream: PrintStream

  def begin(): Unit

  def addAll(toolSpecification: Option[com.codacy.plugins.api.results.Tool.Specification],
             patternDescriptions: Set[PatternDescription],
             elements: Seq[Result]): Unit

  def end(): Unit

}

object Formatter {

  private val logger: Logger = getLogger

  private val defaultPrintStream = Console.out

  val defaultFormatter: FormatterCompanion = Text

  val allFormatters: Set[FormatterCompanion] = Set(defaultFormatter, Json, Sarif)

  def apply(formatterName: String,
            executionDirectory: File,
            outputFile: Option[File] = Option.empty,
            printStream: Option[PrintStream] = Option.empty): Formatter = {

    val builder = allFormatters.find(_.name.equalsIgnoreCase(formatterName)).getOrElse {
      logger.warn(s"Could not find formatter for name $formatterName. Using ${defaultFormatter.name} as fallback.")
      defaultFormatter
    }

    val stream = outputFile.map(asPrintStream).orElse(printStream).getOrElse(defaultPrintStream)

    builder(stream, executionDirectory)
  }

  private def asPrintStream(file: File) = {
    new PrintStream(new FileOutputStream(file.toJava, false))
  }
}
