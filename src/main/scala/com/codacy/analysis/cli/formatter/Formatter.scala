package com.codacy.analysis.cli.formatter

import java.io.{FileOutputStream, PrintStream}

import better.files.File
import com.codacy.analysis.cli.model.Result
import org.log4s.Logger

trait FormatterCompanion {
  def name: String
  def apply(stream: PrintStream): Formatter
}

trait Formatter {

  def stream: PrintStream

  def begin(): Unit

  def add(element: Result): Unit

  def addAll(elements: Seq[Result]): Unit = elements.foreach(add)

  def end(): Unit

}

object Formatter {

  private val defaultPrintStream = Console.out

  val defaultFormatter: FormatterCompanion = Text

  val allFormatters: Set[FormatterCompanion] = Set(defaultFormatter, Json)

  def apply(name: String, file: Option[File] = Option.empty, printStream: Option[PrintStream] = Option.empty)(
    implicit logger: Logger): Formatter = {

    val builder = allFormatters.find(_.name.equalsIgnoreCase(name)).getOrElse {
      logger.warn(s"Could not find formatter for name $name. Using ${defaultFormatter.name} as fallback.")
      defaultFormatter
    }

    val stream = file.map(asPrintStream).orElse(printStream).getOrElse(defaultPrintStream)

    builder(stream)
  }

  private def asPrintStream(file: File) = {
    new PrintStream(new FileOutputStream(file.toJava, true))
  }
}
