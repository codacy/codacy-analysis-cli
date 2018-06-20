package com.codacy.analysis.cli.formatter

import java.io.PrintStream
import java.nio.file.Path

import com.codacy.analysis.cli.model.Result
import com.codacy.plugins.api.results
import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.syntax._

import scala.util.Properties

object Json extends FormatterCompanion {
  val name: String = "json"
  def apply(stream: PrintStream): Formatter = new Json(stream)
}

private[formatter] class Json(val stream: PrintStream) extends Formatter {

  private var alreadyPrinted: Boolean = false
  private implicit val categoryEncoder: Encoder[results.Pattern.Category.Value] =
    Encoder.enumEncoder(results.Pattern.Category)
  private implicit val levelEncoder: Encoder[results.Result.Level.Value] = Encoder.enumEncoder(results.Result.Level)
  private implicit val fileEncoder: Encoder[Path] = Encoder[String].contramap(_.toString)

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
