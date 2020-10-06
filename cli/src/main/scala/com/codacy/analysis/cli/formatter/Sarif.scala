package com.codacy.analysis.cli.formatter

import java.io.PrintStream
import java.math.BigInteger
import java.net.URI
import java.security.MessageDigest
import java.util

import better.files.File
import com.codacy.analysis.core.model.{Issue, Result}
import com.codacy.plugins.api.results.Pattern
import com.codacy.plugins.api.{PatternDescription, results}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Printer}

import scala.collection.JavaConverters._
import scala.util.matching.Regex

object Sarif extends FormatterCompanion {
  override val name: String = "sarif"

  override def apply(outputStream: PrintStream, executionDirectory: File): Formatter =
    new Sarif(outputStream, executionDirectory)
}

private[formatter] class Sarif(val stream: PrintStream, val executionDirectory: File) extends Formatter {

  val schema = new URI("https://docs.oasis-open.org/sarif/sarif/v2.1.0/cos02/schemas/sarif-schema-2.1.0.json")
  val version = "2.1.0"

  private val rootDirectory = "file:///codacy"

  private val spacesCompiledRegex: Regex = "\\s".r

  private implicit val levelEncoder: Encoder[SarifReport.Level.Value] = Encoder.encodeEnumeration(SarifReport.Level)

  private val runs: util.ArrayList[SarifReport.Run] = new util.ArrayList()

  override def begin(): Unit = ()

  override def end(): Unit = {
    val sarifReport = SarifReport(`$schema` = schema.toString, version = version, runs = runs.asScala.toList)
    val printer = Printer.noSpaces.copy(dropNullValues = true)
    val reportString = printer.print(sarifReport.asJson)
    stream.print(reportString)
    stream.flush()

    ()
  }

  override def addAll(toolSpecification: Option[com.codacy.plugins.api.results.Tool.Specification],
                      patternDescriptions: Set[PatternDescription],
                      analysisResults: Seq[Result]): Unit = {
    toolSpecification.foreach { toolSpec =>
      val (nonSecurityIssues, securityIssues) = categorizeIssues(toolSpec, analysisResults)

      val securityRules = createRules(securityIssues, patternDescriptions)
      val nonSecurityRules = createRules(nonSecurityIssues, patternDescriptions)

      val driver = SarifReport.Tool(
        SarifReport.Driver(
          name = s"${toolSpec.name.value.capitalize} (reported by Codacy)",
          version = toolSpec.version.fold("0.0.0-unknown")(_.value.stripPrefix("v")),
          rules = securityRules ++ nonSecurityRules))

      val invocations =
        List(SarifReport
          .Invocation(executionSuccessful = true, workingDirectory = SarifReport.ArtifactLocation(uri = rootDirectory)))

      val artifacts = createArtifacts(securityIssues ++ nonSecurityIssues)

      val securityResults = createResults(securityIssues, artifacts, securityRules, securityIssues = true)
      val nonSecurityResults =
        createResults(nonSecurityIssues, artifacts, nonSecurityRules, securityIssues = false)

      runs.add(
        SarifReport.Run(
          tool = driver,
          results = securityResults ++ nonSecurityResults,
          invocations = invocations,
          artifacts = artifacts))

      ()
    }
  }

  private def categorizeIssues(toolSpec: com.codacy.plugins.api.results.Tool.Specification,
                               analysisResults: Seq[Result]): (Seq[Issue], Seq[Issue]) = {
    // HACK: Seems like the issues (`issue.category`) do not have the right category
    //   while in the specification (`toolSpec.patterns[].category`) the pattern has the right category
    val patternsCategoryMap: Map[String, Pattern.Category] =
      toolSpec.patterns.map(pattern => (pattern.patternId.value, pattern.category))(collection.breakOut)

    val analysisIssues = analysisResults.collect {
      case issue: Issue => issue
    }
    val securityIssues = analysisIssues.filter { issue =>
      patternsCategoryMap.get(issue.patternId.value).contains(Pattern.Category.Security)
    }
    val nonSecurityIssues = analysisIssues.diff(securityIssues)

    (nonSecurityIssues, securityIssues)
  }

  private def createRules(issues: Seq[Issue], patternDescriptions: Set[PatternDescription]): List[SarifReport.Rule] = {
    val patternsMap: Map[String, PatternDescription] =
      patternDescriptions.map(pattern => (pattern.patternId.value, pattern))(collection.breakOut)

    (for {
      issue <- issues.groupBy(_.patternId.value).collect { case (_, issue :: _) => issue }
      modelPattern <- patternsMap.get(issue.patternId.value)
    } yield SarifReport.Rule(
      id = issue.patternId.value,
      name = modelPattern.title,
      shortDescription = SarifReport.Message(modelPattern.description.getOrElse(modelPattern.title)),
      help = SarifReport
        .Message(text = modelPattern.description.getOrElse(modelPattern.title), markdown = modelPattern.explanation),
      properties =
        SarifReport.RuleProperties(category = issue.category.getOrElse(Pattern.Category.CodeStyle).toString))).toList
  }

  private def createArtifacts(issues: Seq[Issue]): List[SarifReport.Artifact] = {
    val filenames: Set[String] = issues.map(_.filename.toString)(collection.breakOut)
    filenames.map(filename => SarifReport.Artifact(SarifReport.ArtifactLocation(s"$rootDirectory/$filename")))(
      collection.breakOut)
  }

  private def createResults(issues: Seq[Issue],
                            artifacts: List[SarifReport.Artifact],
                            rules: List[SarifReport.Rule],
                            securityIssues: Boolean): List[SarifReport.Result] = {
    issues
      .groupBy(_.filename)
      .flatMap {
        case (filePath, issues) =>
          val fileContents = executionDirectory./(filePath.toString).lines.toList

          issues.map { issue =>
            val message = SarifReport.Message(issue.message.text)
            val filePath = s"$rootDirectory/${issue.filename.toString}"
            val locations = List(
              SarifReport.Location(SarifReport.PhysicalLocation(
                artifactLocation = SarifReport
                  .ArtifactLocationIndexed(index = artifacts.indexWhere(_.location.uri == filePath), uri = filePath),
                region = SarifReport.Region(startLine = issue.location.line))))

            SarifReport.Result(
              ruleIndex = rules.indexWhere(_.id == issue.patternId.value),
              ruleId = issue.patternId.value,
              message = message,
              level = getSarifLevel(issue.level, securityIssues),
              locations = locations,
              partialFingerprints =
                SarifReport.PartialFingerprints("1", generatePrimaryLocationHash(issue, fileContents)))
          }
      }(collection.breakOut)
  }

  private def getSarifLevel(level: results.Result.Level.Value, securityIssue: Boolean): SarifReport.Level.Value = {
    level match {
      case results.Result.Level.Err  => if (securityIssue) SarifReport.Level.Error else SarifReport.Level.Warning
      case results.Result.Level.Warn => if (securityIssue) SarifReport.Level.Warning else SarifReport.Level.Note
      case _                         => if (securityIssue) SarifReport.Level.Note else SarifReport.Level.None
    }
  }

  private def generatePrimaryLocationHash(issue: Issue, fileContents: List[String]): String = {
    val issueLineContents = fileContents(Math.max(0, issue.location.line - 1))
    val lineContentsWithoutSpaces = spacesCompiledRegex.replaceAllIn(issueLineContents, "")
    val fingerprintContents =
      (issue.filename.toString + issue.patternId.value + lineContentsWithoutSpaces).getBytes("UTF-8")
    val md5 = MessageDigest.getInstance("MD5")
    md5.update(fingerprintContents, 0, fingerprintContents.length)
    new BigInteger(1, md5.digest()).toString(16)
  }

}

final case class SarifReport(`$schema`: String, version: String, runs: List[SarifReport.Run])

object SarifReport {

  object Level extends Enumeration {
    val Error: Value = Value("error")
    val Warning: Value = Value("warning")
    val Note: Value = Value("note")
    val None: Value = Value("none")
  }

  final case class ArtifactLocation(uri: String)

  final case class ArtifactLocationIndexed(index: Int, uri: String)

  final case class Region(startLine: Int, startColumn: Int = 1)

  final case class PhysicalLocation(artifactLocation: ArtifactLocationIndexed, region: Region)

  final case class Location(physicalLocation: PhysicalLocation)

  final case class PartialFingerprints(primaryLocationStartColumnFingerprint: String, primaryLocationLineHash: String)

  final case class Message(text: String, markdown: Option[String] = None)

  final case class Result(ruleIndex: Int,
                          ruleId: String,
                          message: Message,
                          level: Level.Value,
                          locations: List[Location],
                          partialFingerprints: PartialFingerprints)

  final case class Driver(name: String,
                          version: String,
                          informationUri: String = "https://www.codacy.com",
                          rules: List[Rule])

  final case class RuleProperties(category: String)

  final case class Rule(id: String, name: String, shortDescription: Message, help: Message, properties: RuleProperties)

  final case class Tool(driver: Driver)

  final case class Invocation(executionSuccessful: Boolean, workingDirectory: ArtifactLocation)

  final case class Artifact(location: ArtifactLocation)

  final case class Run(tool: Tool, results: List[Result], invocations: List[Invocation], artifacts: List[Artifact])

}
