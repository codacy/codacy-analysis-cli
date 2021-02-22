package com.codacy.analysis.cli.formatter

import java.io.{FileOutputStream, PrintStream}

import better.files.File
import com.codacy.analysis.cli.configuration.CLIConfiguration
import com.codacy.analysis.core.model.Result
import com.codacy.plugins.api.PatternDescription
import org.log4s.{Logger, getLogger}

trait FormatterCompanion {
  def name: String
  def apply(printStream: PrintStream, executionDirectory: File, ghCodeScanningCompat: Boolean): Formatter
}

trait Formatter {

  def stream: PrintStream

  def begin(): Unit

  def addAll(toolSpecification: Option[com.codacy.plugins.api.results.Tool.Specification],
             patternDescriptions: Set[PatternDescription],
             toolPrefix: Option[String],
             elements: Seq[Result]): Unit

  def end(): Unit

}

object Formatter {

  private val logger: Logger = getLogger

  private val defaultPrintStream = Console.out

  val defaultFormatter: FormatterCompanion = Text

  val allFormatters: Set[FormatterCompanion] = Set(defaultFormatter, Json, Sarif)

  def apply(outputConfiguration: CLIConfiguration.Output,
            executionDirectory: File,
            printStream: Option[PrintStream] = Option.empty): Formatter = {

    val stream = outputConfiguration.file.map(asPrintStream).orElse(printStream).getOrElse(defaultPrintStream)

    val formatterBuilder = allFormatters.find(_.name.equalsIgnoreCase(outputConfiguration.format)).getOrElse {
      logger.warn(
        s"Could not find formatter for name ${outputConfiguration.format}. Using ${defaultFormatter.name} as fallback.")
      defaultFormatter
    }

    formatterBuilder(stream, executionDirectory, outputConfiguration.ghCodeScanningCompat)
  }

  private def asPrintStream(file: File) = {
    new PrintStream(new FileOutputStream(file.toJava, false))
  }
}
