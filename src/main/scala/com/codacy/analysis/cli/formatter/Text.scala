package com.codacy.analysis.cli.formatter

import java.io.PrintStream

import com.codacy.analysis.cli.model._

object Text extends FormatterCompanion {
  val name: String = "text"
  def apply(stream: PrintStream): Formatter = new Text(stream)
}

private[formatter] class Text(val stream: PrintStream) extends Formatter {

  override def begin(): Unit = {
    stream.println("Starting analysis ...")
    stream.flush()
  }

  override def end(): Unit = {
    stream.println("Analysis complete")
    stream.flush()
  }

  def add(element: Result): Unit = {
    element match {
      case Issue(LineLocation(line), filename) =>
        stream.println(s"Found issue in $filename:$line")
        stream.flush()
      case Issue(FullLocation(line, position), filename) =>
        stream.println(s"Found issue in $filename:$line:$position")
        stream.flush()
      case FileError(filename, message) =>
        stream.println(s"Found $message in $filename")
        stream.flush()
    }
  }

}
