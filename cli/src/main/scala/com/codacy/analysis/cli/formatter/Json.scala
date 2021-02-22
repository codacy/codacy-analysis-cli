package com.codacy.analysis.cli.formatter

import java.io.PrintStream
import java.nio.file.Path

import better.files.File
import com.codacy.analysis.core.model.Result
import com.codacy.plugins.api.{PatternDescription, results}
import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.syntax._

import scala.util.Properties

object Json extends FormatterCompanion {
  override val name: String = "json"

  override def apply(printStream: PrintStream, executionDirectory: File, ghCodeScanningCompat: Boolean): Formatter =
    new Json(printStream)
}

private[formatter] class Json(val stream: PrintStream) extends Formatter {

  private var alreadyPrinted: Boolean = false

  private implicit val categoryEncoder: Encoder[results.Pattern.Category.Value] =
    Encoder.encodeEnumeration(results.Pattern.Category)

  private implicit val levelEncoder: Encoder[results.Result.Level.Value] =
    Encoder.encodeEnumeration(results.Result.Level)
  private implicit val fileEncoder: Encoder[Path] = Encoder[String].contramap(_.toString)

  override def begin(): Unit = {
    stream.print("[")
  }

  override def end(): Unit = {
    stream.print("]")
    stream.print(Properties.lineSeparator)
    stream.flush()
  }

  override def addAll(toolSpecification: Option[com.codacy.plugins.api.results.Tool.Specification],
                      patternDescriptions: Set[PatternDescription],
                      toolPrefix: Option[String],
                      elements: Seq[Result]): Unit = elements.foreach(add)

  private def add(element: Result): Unit = {
    if (alreadyPrinted) stream.print(",") else alreadyPrinted = true
    stream.print(element.asJson.noSpaces)
  }

}
