package com.codacy.analysis.cli.formatter

import java.io.{FileOutputStream, PrintStream}

import better.files.File
import com.codacy.analysis.cli.model.Result
import org.log4s.Logger

trait Formatter {

  def stream: PrintStream

  def name: String

  def begin(): Unit

  def add(element: Result): Unit

  def addAll(elements: Seq[Result]): Unit = elements.foreach(add)

  def end(): Unit

}

object Formatter {

  private val defaultPrintStream = Console.out

  def apply(name: String, file: Option[File] = Option.empty, printStream: Option[PrintStream] = Option.empty)(
    implicit logger: Logger): Formatter = {
    val builder = name.toLowerCase match {
      case "json" => new Json(_)
      case "text" => new Text(_)
      case _ =>
        logger.warn(s"Could not find formatter for name $name using text as fallback")
        new Text(_)
    }

    val stream = file.map(asPrintStream).orElse(printStream).getOrElse(defaultPrintStream)

    builder(stream)
  }

  private def asPrintStream(file: File) = {
    new PrintStream(new FileOutputStream(file.toJava, true))
  }
}
