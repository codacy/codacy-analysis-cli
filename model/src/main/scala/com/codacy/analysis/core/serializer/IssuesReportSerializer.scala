package com.codacy.analysis.core.serializer

import java.nio.file.Path

import com.codacy.analysis.core.model.IssuesAnalysis.FileResults
import com.codacy.analysis.core.model.{Issue, IssuesAnalysis, Location, ToolResult, ToolResults}
import com.codacy.plugins.api.results
import io.circe.{Encoder, Printer}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._

object IssuesReportSerializer {

  private[IssuesReportSerializer] implicit val levelEncoder: Encoder[results.Result.Level.Value] =
    Encoder.encodeEnumeration(results.Result.Level)

  private[IssuesReportSerializer] implicit val categoryEncoder: Encoder[results.Pattern.Category.Value] =
    Encoder.encodeEnumeration(results.Pattern.Category)

  private[IssuesReportSerializer] implicit val pathEncoder: Encoder[Path] = Encoder.encodeString.contramap(_.toString)

  private[IssuesReportSerializer] implicit val toolResultsEncoder: Encoder[ToolResults] = deriveEncoder
  private[IssuesReportSerializer] implicit val issuesAnalysisEncoder: Encoder[IssuesAnalysis] = deriveEncoder
  private[IssuesReportSerializer] implicit val issueResultEncoder: Encoder[Issue] = deriveEncoder
  private[IssuesReportSerializer] implicit val patternIdEncoder: Encoder[results.Pattern.Id] = deriveEncoder
  private[IssuesReportSerializer] implicit val issueMessageEncoder: Encoder[Issue.Message] = deriveEncoder
  private[IssuesReportSerializer] implicit val issueLocationEncoder: Encoder[Location] = deriveEncoder
  private[IssuesReportSerializer] implicit val resultEncoder: Encoder[ToolResult] = deriveEncoder
  private[IssuesReportSerializer] implicit val fileResultsEncoder: Encoder[FileResults] = deriveEncoder

  def toJsonString(toolResults: Set[ToolResults]): String =
    toolResults.asJson.printWith(Printer.noSpaces.copy(dropNullValues = true))
}
