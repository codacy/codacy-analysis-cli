package com.codacy.analysis.cli

import better.files.File
import com.codacy.analysis.cli.analysis.{AnalyseExecutor, ToolSelector}
import com.codacy.analysis.cli.configuration.CLIConfiguration
import com.codacy.analysis.cli.formatter.{Formatter, Json}
import com.codacy.analysis.core.ToolRepositoryMock
import com.codacy.analysis.core.analysis.{Analyser, CodacyPluginsAnalyser}
import com.codacy.analysis.core.clients.api._
import com.codacy.analysis.core.files.FileCollector
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.utils.TestUtils._
import io.circe.generic.auto._
import io.circe.parser
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.duration._
import scala.util.Try

class AnalyseExecutorSpec extends Specification with NoLanguageFeatures with Mockito with FutureMatchers {

  "AnalyseExecutor" should {

    val pyLintPatternsInternalIds = Set("PyLint_C0111", "PyLint_E1101")
    val pathToIgnore = "lib/improver/tests/"

    s"""|analyze a python project with pylint, using a remote project configuration retrieved with a project token
        | that ignores the files that start with the path $pathToIgnore
        | and considers just patterns ${pyLintPatternsInternalIds.mkString(", ")}""".stripMargin in {
      val commitUuid = "9232dbdcae98b19412c8dd98c49da8c391612bfa"
      withClonedRepo("git@github.com:qamine-test/improver.git", commitUuid) { (file, directory) =>
        val toolPatterns = pyLintPatternsInternalIds.map { patternId =>
          CLIConfiguration.IssuesTool.Pattern(patternId, Set.empty)
        }

        val configuration = analysisConfiguration(
          directory,
          file,
          Option("pylint"),
          Set(
            CLIConfiguration.IssuesTool(
              uuid = "34225275-f79e-4b85-8126-c7512c987c0d",
              enabled = true,
              notEdited = false,
              toolPatterns)),
          Set(FilePath(pathToIgnore)))

        runAnalyseExecutor(configuration)

        val result = for {
          responseJson <- parser.parse(file.contentAsString)
          response <- responseJson.as[Set[ToolResult]]
        } yield response

        result must beRight
        result must beLike {
          case Right(response: Set[ToolResult]) =>
            response.size must beGreaterThan(0)

            response.forall {
              case i: Issue => !i.filename.startsWith(pathToIgnore)
              case _        => true
            } must beTrue

            response.forall {
              case i: Issue => pyLintPatternsInternalIds.contains(i.patternId.value)
              case _        => true
            } must beTrue
        }
      }
    }

    val esLintPatternsInternalIds =
      Set("ESLint_semi", "ESLint_no-undef", "ESLint_indent", "ESLint_no-empty")

    s"""|analyze a javascript project with eslint, using a remote project configuration retrieved with an api token
        | that considers just patterns ${esLintPatternsInternalIds.mkString(", ")}""".stripMargin in {
      val commitUuid = "9232dbdcae98b19412c8dd98c49da8c391612bfa"
      withClonedRepo("git@github.com:qamine-test/Monogatari.git", commitUuid) { (file, directory) =>
        val toolPatterns = esLintPatternsInternalIds.map { patternId =>
          CLIConfiguration.IssuesTool.Pattern(patternId, Set.empty)
        }
        val configuration = analysisConfiguration(
          directory,
          file,
          Option("eslint"),
          Set(
            CLIConfiguration.IssuesTool(
              uuid = "cf05f3aa-fd23-4586-8cce-5368917ec3e5",
              enabled = true,
              notEdited = false,
              toolPatterns)),
          Set.empty)

        runAnalyseExecutor(configuration)

        val result = for {
          responseJson <- parser.parse(file.contentAsString)
          response <- responseJson.as[Set[Result]]
        } yield response

        result must beRight
        result must beLike {
          case Right(response: Set[Result]) =>
            response.size must beGreaterThan(0)

            response.forall {
              case i: Issue => esLintPatternsInternalIds.contains(i.patternId.value)
              case _        => true
            } must beTrue
        }
      }
    }

    val cssLintPatternsInternalIds = Set("CSSLint_important")

    "analyze a javascript and css project" in {
      val commitUuid = "9232dbdcae98b19412c8dd98c49da8c391612bfa"
      withClonedRepo("git@github.com:qamine-test/Monogatari.git", commitUuid) { (file, directory) =>
        val configuration = analysisConfiguration(
          directory,
          file,
          Option.empty,
          Set(
            CLIConfiguration.IssuesTool(
              uuid = "cf05f3aa-fd23-4586-8cce-5368917ec3e5",
              enabled = true,
              notEdited = false,
              patterns = esLintPatternsInternalIds.map { patternId =>
                CLIConfiguration.IssuesTool.Pattern(patternId, Set.empty)
              }),
            CLIConfiguration.IssuesTool(
              uuid = "997201eb-0907-4823-87c0-a8f7703531e7",
              enabled = true,
              notEdited = true,
              patterns = cssLintPatternsInternalIds.map { patternId =>
                CLIConfiguration.IssuesTool.Pattern(patternId, Set.empty)
              })),
          Set.empty)

        runAnalyseExecutor(configuration)

        val result = for {
          responseJson <- parser.parse(file.contentAsString)
          response <- responseJson.as[Set[Result]]
        } yield response

        result must beRight
        result must beLike {
          case Right(response: Set[Result]) =>
            response.size must beGreaterThan(0)

            response.collect {
              case i: Issue => i.patternId.value
            } must containAllOf((esLintPatternsInternalIds ++ cssLintPatternsInternalIds).toSeq)
        }
      }
    }
  }

  private def runAnalyseExecutor(configuration: CLIConfiguration.Analysis) = {
    val formatter: Formatter = Formatter(configuration.output, configuration.projectDirectory)
    val analyser: Analyser = new CodacyPluginsAnalyser()
    val fileCollector: FileCollector[Try] = FileCollector.defaultCollector()

    val toolRepository = ToolRepositoryMock
    val toolSelector = new ToolSelector(toolRepository)
    new AnalyseExecutor(formatter, analyser, fileCollector, configuration, toolSelector).run() must beRight
  }

  private def analysisConfiguration(directory: File,
                                    outputFile: File,
                                    tool: Option[String],
                                    toolConfigs: Set[CLIConfiguration.IssuesTool],
                                    ignoredPaths: Set[FilePath]): CLIConfiguration.Analysis = {
    val fileExclusions = CLIConfiguration.FileExclusionRules(
      Some(Set.empty),
      ignoredPaths,
      CLIConfiguration.FileExclusionRules.ExcludePaths(Set.empty, Map.empty),
      Map.empty)

    val toolConfiguration =
      CLIConfiguration.Tool(Option(15.minutes), allowNetwork = false, Right(toolConfigs), Option.empty, Map.empty)
    CLIConfiguration.Analysis(
      directory,
      CLIConfiguration.Output(Json.name, Option(outputFile), ghCodeScanningCompat = false),
      tool,
      Option.empty,
      forceFilePermissions = false,
      fileExclusions,
      toolConfiguration)
  }
}
