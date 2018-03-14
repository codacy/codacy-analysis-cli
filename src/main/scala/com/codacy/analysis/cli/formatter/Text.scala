package com.codacy.analysis.cli.formatter

import java.io.PrintStream

import com.codacy.analysis.cli.model.{FileError, Result}

class Text(val stream: PrintStream) extends Formatter {

  override val name: String = "json"

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
      case FileError(filename, message) =>
        stream.println(s"Found $message in $filename")
        stream.flush()
      case _ =>
    }
  }

}
