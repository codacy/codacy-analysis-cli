package com.codacy.analysis.cli

import com.codacy.analysis.cli.analysis.Analyser
import com.codacy.analysis.cli.clients.api._
import com.codacy.analysis.cli.clients.{APIToken, CodacyClient, Credentials, ProjectToken}
import com.codacy.analysis.cli.command._
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.files.FileCollector
import com.codacy.analysis.cli.formatter.{Formatter, Json}
import com.codacy.analysis.cli.model.{Issue, Result}
import com.codacy.analysis.cli.upload.ResultsUploader
import com.codacy.analysis.cli.utils.HttpHelper
import com.codacy.analysis.cli.utils.TestUtils._
import io.circe.generic.auto._
import io.circe.parser
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.util.Try

class AnalyseExecutorSpec extends Specification with NoLanguageFeatures with Mockito with FutureMatchers {

  "AnalyseExecutor" should {

    val pyLintPatternsInternalIds = Set("PyLint_C0111", "PyLint_E1101")
    val pathToIgnore = "lib/improver/tests/"

    s"""analyse a python project with pylint, using a remote project configuration retrieved with a project token
       | that ignores the files that start with the path $pathToIgnore
       | and considers just patterns ${pyLintPatternsInternalIds.mkString(", ")}""".stripMargin in {
      val commitUuid = "9232dbdcae98b19412c8dd98c49da8c391612bfa"
      withClonedRepo("git://github.com/qamine-test/improver.git", commitUuid) { (file, directory) =>
        val projTokenStr = "RandomProjectToken"
        val analyse = Analyse(
          options = CommonOptions(),
          api = APIOptions(projectToken = Option(projTokenStr), codacyApiBaseUrl = Some("codacy.com")),
          tool = Option("pylint"),
          directory = Option(directory),
          format = Json.name,
          output = Option(file),
          extras = ExtraOptions(),
          commitUuid = Option(commitUuid))
        val toolPatterns = pyLintPatternsInternalIds.map { patternId =>
          ToolPattern(patternId, Set.empty)
        }
        val remoteConfiguration: Either[String, ProjectConfiguration] =
          Right(
            ProjectConfiguration(
              Set(FilePath(pathToIgnore)),
              Set.empty,
              Set(
                ToolConfiguration(
                  "34225275-f79e-4b85-8126-c7512c987c0d",
                  isEnabled = true,
                  notEdited = false,
                  toolPatterns))))

        runAnalyseExecutor(analyse, remoteConfiguration, Left("No uploader available"))

        val result = for {
          responseJson <- parser.parse(file.contentAsString)
          response <- responseJson.as[Set[Result]]
        } yield response

        result must beRight
        result must beLike {
          case Right(response: Set[Result]) =>
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

    val esLintPatternsInternalIds = Set("ESLint_semi", "ESLint_no-undef", "ESLint_indent", "ESLint_no-empty")

    s"""analyse a javascript project with eslint, using a remote project configuration retrieved with an api token
       | that considers just patterns ${esLintPatternsInternalIds.mkString(", ")}""".stripMargin in {
      val commitUuid = "9232dbdcae98b19412c8dd98c49da8c391612bfa"
      withClonedRepo("git://github.com/qamine-test/Monogatari.git", commitUuid) { (file, directory) =>
        val apiTokenStr = "RandomApiToken"
        val username = "some_user"
        val project = "some_project"
        val analyse = Analyse(
          options = CommonOptions(),
          api = APIOptions(
            apiToken = Option(apiTokenStr),
            username = Option(username),
            project = Option(project),
            codacyApiBaseUrl = Option("codacy.com")),
          tool = Option("eslint"),
          directory = Option(directory),
          format = Json.name,
          output = Option(file),
          extras = ExtraOptions(),
          commitUuid = Option(commitUuid))
        val toolPatterns = esLintPatternsInternalIds.map { patternId =>
          ToolPattern(patternId, Set.empty)
        }
        val credentials: Credentials = APIToken(apiTokenStr, None, username, project)
        val remoteConfiguration: Either[String, ProjectConfiguration] =
          Right(
            ProjectConfiguration(
              Set.empty,
              Set.empty,
              Set(
                ToolConfiguration(
                  "cf05f3aa-fd23-4586-8cce-5368917ec3e5",
                  isEnabled = true,
                  notEdited = false,
                  toolPatterns))))

        runAnalyseExecutor(analyse, remoteConfiguration, Left("No uploader available"))

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

    "analyse a javascript and css project " in {
      val commitUuid = "9232dbdcae98b19412c8dd98c49da8c391612bfa"
      withClonedRepo("git://github.com/qamine-test/Monogatari.git", commitUuid) { (file, directory) =>
        val apiTokenStr = "RandomApiToken"
        val username = "some_user"
        val project = "some_project"
        val analyse = Analyse(
          options = CommonOptions(),
          api = APIOptions(
            apiToken = Option(apiTokenStr),
            username = Option(username),
            project = Option(project),
            codacyApiBaseUrl = Option("codacy.com")),
          tool = None,
          directory = Option(directory),
          format = Json.name,
          output = Option(file),
          extras = ExtraOptions(),
          commitUuid = Option(commitUuid))

        val remoteConfiguration: Either[String, ProjectConfiguration] =
          Right(
            ProjectConfiguration(
              Set.empty,
              Set.empty,
              Set(
                ToolConfiguration(
                  "cf05f3aa-fd23-4586-8cce-5368917ec3e5",
                  isEnabled = true,
                  notEdited = false,
                  esLintPatternsInternalIds.map { patternId =>
                    ToolPattern(patternId, Set.empty)
                  }),
                ToolConfiguration(
                  "997201eb-0907-4823-87c0-a8f7703531e7",
                  isEnabled = true,
                  notEdited = true,
                  cssLintPatternsInternalIds.map { patternId =>
                    ToolPattern(patternId, Set.empty)
                  }))))

        runAnalyseExecutor(analyse, remoteConfiguration, Left("No uploader available"))

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

  private def runAnalyseExecutor(analyse: Analyse,
                                 remoteProjectConfiguration: Either[String, ProjectConfiguration],
                                 resultsUploaderEither: Either[String, ResultsUploader]) = {
    val formatter: Formatter = Formatter(analyse.format, analyse.output)
    val analyser: Analyser[Try] = Analyser(analyse.extras.analyser)
    val fileCollector: FileCollector[Try] = FileCollector.defaultCollector()

    // scalafix:off
    new AnalyseExecutor(
      analyse.tool,
      analyse.directory,
      formatter,
      analyser,
      resultsUploaderEither,
      fileCollector,
      remoteProjectConfiguration).run() must beRight.await
    // scalafix:on
  }

}
