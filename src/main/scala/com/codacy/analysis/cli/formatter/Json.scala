package com.codacy.analysis.cli.formatter

import java.io.PrintStream

import com.codacy.analysis.cli.model.Result
import io.circe.generic.auto._
import io.circe.syntax._

import scala.util.Properties

class Json(val stream: PrintStream) extends Formatter {

  override val name: String = "json"

  override def begin(): Unit = {
    stream.print("[")
  }

  override def end(): Unit = {
    stream.print("]")
    stream.print(Properties.lineSeparator)
    stream.flush()
  }

  def add(element: Result): Unit = {
    stream.print(element.asJson.noSpaces)
    stream.print(",")
  }

}
