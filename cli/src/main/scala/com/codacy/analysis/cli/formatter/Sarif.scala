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
                      results: Seq[Result]): Unit = {
    toolSpecification.foreach {
      case toolSpec: com.codacy.plugins.api.results.Tool.Specification
          if toolSpec.patterns.exists(_.category == Pattern.Category.Security) =>
        val patternsMap: Map[String, PatternDescription] =
          patternDescriptions.map(pattern => (pattern.patternId.value, pattern))(collection.breakOut)

        val securityIssues = results.collect {
          case issue: Issue if issue.category.contains(Pattern.Category.Security) => issue
        }

        val rules = {
          (for {
            issue <- securityIssues.groupBy(_.patternId.value).collect { case (_, issue :: _) => issue }
            modelPattern <- patternsMap.get(issue.patternId.value)
          } yield SarifReport.Rule(
            id = issue.patternId.value,
            name = modelPattern.title,
            shortDescription = SarifReport.Message(modelPattern.description.getOrElse(modelPattern.title)),
            help = SarifReport.Message(
              text = modelPattern.description.getOrElse(modelPattern.title),
              markdown = modelPattern.explanation),
            properties = SarifReport.RuleProperties(category =
              issue.category.getOrElse(Pattern.Category.CodeStyle).toString))).toList
        }

        val driver = SarifReport.Tool(
          SarifReport.Driver(
            name = s"${toolSpec.name.value.capitalize} (reported by Codacy)",
            version = toolSpec.version.fold("0.0.0-unknown")(_.value.stripPrefix("v")),
            rules = rules))

        val invocations =
          List(
            SarifReport.Invocation(
              executionSuccessful = true,
              workingDirectory = SarifReport.ArtifactLocation(uri = rootDirectory)))

        val artifacts: List[SarifReport.Artifact] = {
          val filenames: Set[String] = securityIssues.map(_.filename.toString)(collection.breakOut)
          filenames.map(filename => SarifReport.Artifact(SarifReport.ArtifactLocation(s"$rootDirectory/$filename")))(
            collection.breakOut)
        }

        val sarifResults: List[SarifReport.Result] = securityIssues
          .groupBy(_.filename)
          .flatMap {
            case (filePath, issues) =>
              val fileContents = executionDirectory./(filePath.toString).lines.toList
              issues.map { issue =>
                val message = SarifReport.Message(issue.message.text)
                val filePath = s"$rootDirectory/${issue.filename.toString}"
                val locations = List(SarifReport.Location(SarifReport.PhysicalLocation(
                  artifactLocation = SarifReport
                    .ArtifactLocationIndexed(index = artifacts.indexWhere(_.location.uri == filePath), uri = filePath),
                  region = SarifReport.Region(startLine = issue.location.line))))

                SarifReport.Result(
                  ruleIndex = rules.indexWhere(_.id == issue.patternId.value),
                  ruleId = issue.patternId.value,
                  message = message,
                  level = getSarifLevel(issue.level),
                  locations = locations,
                  partialFingerprints =
                    SarifReport.PartialFingerprints("1", generatePrimaryLocationHash(issue, fileContents)))
              }
          }(collection.breakOut)

        runs.add(
          SarifReport.Run(tool = driver, results = sarifResults, invocations = invocations, artifacts = artifacts))

        ()

      case _ => // Do nothing
    }
  }

  private def getSarifLevel(level: results.Result.Level.Value): SarifReport.Level.Value = {
    level match {
      case results.Result.Level.Err  => SarifReport.Level.Error
      case results.Result.Level.Warn => SarifReport.Level.Warning
      case _                         => SarifReport.Level.Note
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
