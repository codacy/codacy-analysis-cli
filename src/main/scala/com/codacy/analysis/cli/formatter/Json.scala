package com.codacy.analysis.cli.formatter

import java.io.PrintStream

import com.codacy.analysis.cli.model.Result
import io.circe.generic.auto._
import io.circe.syntax._

import scala.util.Properties

object Json extends FormatterCompanion {
  val name: String = "json"
  def apply(stream: PrintStream): Formatter = new Json(stream)
}

private[formatter] class Json(val stream: PrintStream) extends Formatter {

  private var alreadyPrinted: Boolean = false

  override def begin(): Unit = {
    stream.print("[")
  }

  override def end(): Unit = {
    stream.print("]")
    stream.print(Properties.lineSeparator)
    stream.flush()
  }

  def add(element: Result): Unit = {
    if (alreadyPrinted) stream.print(",") else alreadyPrinted = true
    stream.print(element.asJson.noSpaces)
  }

}
