package com.codacy.analysis.cli.formatter

import java.io.{ByteArrayOutputStream, PrintStream}

import better.files.File
import com.codacy.analysis.core.model.{Issue, LineLocation}
import com.codacy.plugins.api.PatternDescription
import com.codacy.plugins.api.results.{Pattern, Result, Tool}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Printer}
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

class SarifSpec extends Specification with NoLanguageFeatures {

  private implicit val levelEncoder: Encoder[SarifReport.Level.Value] = Encoder.encodeEnumeration(SarifReport.Level)

  "Sarif" should {

    "output correct json in sarif format" in {
      File.temporaryDirectory("sarif-formatter") { sourceDirectory =>
        // Arrange
        val sourceFile = sourceDirectory / "test.js" write "line 1\nline 2"
        val patternId = Pattern.Id("custom-pattern-id")
        val patternLevel = Result.Level.Err
        val patternCategory = Pattern.Category.Security
        val toolSpecification = Tool.Specification(
          Tool.Name("Custom Tool"),
          Option(Tool.Version("1.0.0")),
          Set(Pattern.Specification(patternId, patternLevel, patternCategory, None)))
        val patternDescriptions = PatternDescription(
          patternId,
          "My Pattern Title",
          None,
          Option("My Pattern Description"),
          None,
          Option("My Pattern Explanation"))
        val issue = Issue(
          patternId,
          sourceFile.path.getFileName,
          Issue.Message("My Issue Message"),
          patternLevel,
          Option(patternCategory),
          LineLocation(1))

        val expectedArtifactLocation = s"file:///codacy/${issue.filename.toString}"
        val expectedReport = SarifReport(
          "https://docs.oasis-open.org/sarif/sarif/v2.1.0/cos02/schemas/sarif-schema-2.1.0.json",
          "2.1.0",
          List(
            SarifReport.Run(
              SarifReport.Tool(SarifReport.Driver(
                s"${toolSpecification.name.value} (reported by Codacy)",
                version = toolSpecification.version.get.value,
                rules = List(SarifReport.Rule(
                  patternId.value,
                  patternDescriptions.title,
                  SarifReport.Message(patternDescriptions.description.get),
                  SarifReport.Message(patternDescriptions.description.get, patternDescriptions.explanation),
                  SarifReport.RuleProperties(patternCategory.toString))))),
              List(SarifReport.Result(
                0,
                patternId.value,
                SarifReport.Message(issue.message.text),
                SarifReport.Level.Error,
                List(SarifReport.Location(SarifReport.PhysicalLocation(
                  SarifReport.ArtifactLocationIndexed(0, expectedArtifactLocation),
                  SarifReport.Region(issue.location.line)))),
                SarifReport.PartialFingerprints("1", "13591d66e1bfea541894b63c43fd323c"))),
              List(
                SarifReport.Invocation(
                  executionSuccessful = true,
                  workingDirectory = SarifReport.ArtifactLocation("file:///codacy"))),
              List(SarifReport.Artifact(SarifReport.ArtifactLocation(expectedArtifactLocation))))))
        val expectedReportJson = Printer.noSpaces.copy(dropNullValues = true).print(expectedReport.asJson)

        // Act
        val bos = new ByteArrayOutputStream()
        val printStream = new PrintStream(bos)
        val formatter = Sarif.apply(printStream, sourceDirectory)
        formatter.begin()
        formatter.addAll(
          toolSpecification = Option(toolSpecification),
          patternDescriptions = Set(patternDescriptions),
          elements = List(issue))
        formatter.end()
        val output = bos.toString
        bos.close()

        // Assert
        output must beEqualTo(expectedReportJson)
      }
    }

  }

}
