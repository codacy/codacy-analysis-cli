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

        val securityPatternId = Pattern.Id("security-pattern-id")
        val securityPatternLevel = Result.Level.Err
        val securityPatternCategory = Pattern.Category.Security
        val securityPatternDescription = PatternDescription(
          securityPatternId,
          "My Security Pattern Title",
          None,
          Option("My Security Pattern Description"),
          None,
          Option("My Security Pattern Explanation"))
        val securityIssue = Issue(
          securityPatternId,
          sourceFile.path.getFileName,
          Issue.Message("My Security Issue Message"),
          securityPatternLevel,
          Option(securityPatternCategory),
          LineLocation(1))

        val duplicationPatternId = Pattern.Id("duplication-pattern-id")
        val duplicationPatternLevel = Result.Level.Err
        val duplicationPatternCategory = Pattern.Category.Duplication
        val duplicationPatternDescription = PatternDescription(
          duplicationPatternId,
          "My Duplication Pattern Title",
          None,
          Option("My Duplication Pattern Description"),
          None,
          Option("My Duplication Pattern Explanation"))
        val duplicationIssue = Issue(
          duplicationPatternId,
          sourceFile.path.getFileName,
          Issue.Message("My Duplication Issue Message"),
          duplicationPatternLevel,
          Option(duplicationPatternCategory),
          LineLocation(2))

        val toolSpecification = Tool.Specification(
          Tool.Name("Custom Tool"),
          Option(Tool.Version("1.0.0")),
          Set(
            Pattern.Specification(securityPatternId, securityPatternLevel, securityPatternCategory, None),
            Pattern.Specification(duplicationPatternId, duplicationPatternLevel, duplicationPatternCategory, None)))

        val expectedArtifactLocation = s"file:///codacy/${securityIssue.filename.toString}"
        val expectedReport = SarifReport(
          "https://docs.oasis-open.org/sarif/sarif/v2.1.0/cos02/schemas/sarif-schema-2.1.0.json",
          "2.1.0",
          List(
            SarifReport.Run(
              SarifReport.Tool(SarifReport.Driver(
                s"${toolSpecification.name.value} (reported by Codacy)",
                version = toolSpecification.version.get.value,
                rules = List(
                  SarifReport.Rule(
                    securityPatternId.value,
                    securityPatternDescription.title,
                    SarifReport.Message(securityPatternDescription.description.get),
                    SarifReport
                      .Message(securityPatternDescription.description.get, securityPatternDescription.explanation),
                    SarifReport.RuleProperties(securityPatternCategory.toString)),
                  SarifReport.Rule(
                    duplicationPatternId.value,
                    duplicationPatternDescription.title,
                    SarifReport.Message(duplicationPatternDescription.description.get),
                    SarifReport.Message(
                      duplicationPatternDescription.description.get,
                      duplicationPatternDescription.explanation),
                    SarifReport.RuleProperties(duplicationPatternCategory.toString))))),
              List(
                SarifReport.Result(
                  0,
                  securityPatternId.value,
                  SarifReport.Message(securityIssue.message.text),
                  SarifReport.Level.Error,
                  List(SarifReport.Location(SarifReport.PhysicalLocation(
                    SarifReport.ArtifactLocationIndexed(0, expectedArtifactLocation),
                    SarifReport.Region(securityIssue.location.line)))),
                  SarifReport.PartialFingerprints("1", "58405b1dbe14c855b9ae39a4ac1f6a")),
                SarifReport.Result(
                  0,
                  duplicationPatternId.value,
                  SarifReport.Message(duplicationIssue.message.text),
                  SarifReport.Level.Warning,
                  List(SarifReport.Location(SarifReport.PhysicalLocation(
                    SarifReport.ArtifactLocationIndexed(0, expectedArtifactLocation),
                    SarifReport.Region(duplicationIssue.location.line)))),
                  SarifReport.PartialFingerprints("1", "556f62f844c32ae92c4b9591faa54dec"))),
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
          patternDescriptions = Set(securityPatternDescription, duplicationPatternDescription),
          elements = List(securityIssue, duplicationIssue))
        formatter.end()
        val output = bos.toString
        bos.close()

        // Assert
        output must beEqualTo(expectedReportJson)
      }
    }

  }

}
