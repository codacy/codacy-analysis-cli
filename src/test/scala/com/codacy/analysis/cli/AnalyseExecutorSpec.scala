package com.codacy.analysis.cli

import com.codacy.analysis.cli.analysis.Analyser
import com.codacy.analysis.cli.clients.api._
import com.codacy.analysis.cli.clients.{APIToken, CodacyClient, ProjectToken}
import com.codacy.analysis.cli.command._
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.configuration.RemoteConfigurationFetcher
import com.codacy.analysis.cli.files.FileCollector
import com.codacy.analysis.cli.formatter.{Formatter, Json}
import com.codacy.analysis.cli.model.{Issue, Result}
import com.codacy.analysis.cli.utils.TestUtils._
import io.circe.generic.auto._
import io.circe.parser
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

import scala.util.Try

class AnalyseExecutorSpec extends Specification with NoLanguageFeatures {

  "AnalyseExecutor" should {

    val pyLintPatternsInternalIds = Set("PyLint_C0111", "PyLint_E1101")
    val pathToIgnore = "lib/improver/tests/"

    s"""analyse a python project with pylint, using a remote project configuration retrieved with a project token
       | that ignores the files that start with the path $pathToIgnore
       | and considers just patterns ${pyLintPatternsInternalIds.mkString(", ")}""".stripMargin in {
      withClonedRepo[Set[Result]](
        "git://github.com/qamine-test/improver.git",
        "f4c45e59cc9ec2c01b2545b6b49334694a29e0da") { (file, directory) =>
        val projTokenStr = "RandomProjectToken"
        val analyse = Analyse(
          options = CommonOptions(),
          api = APIOptions(projectToken = Option(projTokenStr), codacyApiBaseUrl = Some("codacy.com")),
          tool = "pylint",
          directory = Some(directory),
          format = Json.name,
          output = Some(file),
          extras = ExtraOptions())
        val toolPatterns = pyLintPatternsInternalIds.map { patternId =>
          ToolPattern(patternId, Set.empty)
        }

        val codacyClient = new CodacyClient(None, Map.empty) {
          override def getProjectConfiguration: Either[String, ProjectConfiguration] = {
            Right(
              ProjectConfiguration(
                Set(FilePath(pathToIgnore)),
                Set.empty,
                Set(
                  ToolConfiguration(
                    "34225275-f79e-4b85-8126-c7512c987c0d",
                    isEnabled = true,
                    notEdited = true,
                    toolPatterns))))
          }
        }

        val remoteConfigurationFetcher =
          new RemoteConfigurationFetcher(codacyClient)
        runAnalyseExecuter(
          analyse,
          remoteConfigurationFetcher.getRemoteConfiguration(ProjectToken(projTokenStr), analyse))

        val result = for {
          responseJson <- parser.parse(file.contentAsString)
          response <- responseJson.as[Set[Result]]
        } yield response

        result must beRight
        result must beLike {
          case Right(response: Set[Result]) =>
            response.size must beGreaterThan(0)

            response.exists {
              case i: Issue => i.filename.startsWith(pathToIgnore)
              case _        => false
            } must beFalse

            response.exists {
              case i: Issue => !pyLintPatternsInternalIds.contains(i.patternId.value)
              case _        => false
            } must beFalse
        }
      }
    }

    val esLintPatternsInternalIds = Set("ESLint_semi", "ESLint_no-undef", "ESLint_indent", "ESLint_no-empty")

    s"""analyse a javascript project with eslint, using a remote project configuration retrieved with an api token
       | that considers just patterns ${esLintPatternsInternalIds.mkString(", ")}""".stripMargin in {
      withClonedRepo[Set[Result]](
        "git://github.com/qamine-test/Monogatari.git",
        "9232dbdcae98b19412c8dd98c49da8c391612bfa") { (file, directory) =>
        val apiTokenStr = "RandomApiToken"
        val analyse = Analyse(
          options = CommonOptions(),
          api = APIOptions(
            apiToken = Option(apiTokenStr),
            username = Option("some_user"),
            project = Option("some_project"),
            codacyApiBaseUrl = Some("codacy.com")),
          tool = "eslint",
          directory = Some(directory),
          format = Json.name,
          output = Some(file),
          extras = ExtraOptions())
        val toolPatterns = esLintPatternsInternalIds.map { patternId =>
          ToolPattern(patternId, Set.empty)
        }

        val codacyClient = new CodacyClient(None, Map.empty) {
          override def getProjectConfiguration(projectName: String,
                                               username: String): Either[String, ProjectConfiguration] = {
            Right(
              ProjectConfiguration(
                Set.empty,
                Set.empty,
                Set(
                  ToolConfiguration(
                    "cf05f3aa-fd23-4586-8cce-5368917ec3e5",
                    isEnabled = true,
                    notEdited = true,
                    toolPatterns))))
          }
        }

        val remoteConfigurationFetcher =
          new RemoteConfigurationFetcher(codacyClient)
        runAnalyseExecuter(analyse, remoteConfigurationFetcher.getRemoteConfiguration(APIToken(apiTokenStr), analyse))

        val result = for {
          responseJson <- parser.parse(file.contentAsString)
          response <- responseJson.as[Set[Result]]
        } yield response

        result must beRight
        result must beLike {
          case Right(response: Set[Result]) =>
            response.size must beGreaterThan(0)

            response.exists {
              case i: Issue => !esLintPatternsInternalIds.contains(i.patternId.value)
              case _        => false
            } must beFalse
        }
      }
    }
  }

  private def runAnalyseExecuter(analyse: Analyse,
                                 remoteProjectConfiguration: Either[String, ProjectConfiguration]): Unit = {
    val formatter: Formatter = Formatter(analyse.format, analyse.output)
    val analyser: Analyser[Try] = Analyser(analyse.extras.analyser)
    val fileCollector: FileCollector[Try] = FileCollector.defaultCollector()
    new AnalyseExecutor(analyse, formatter, analyser, fileCollector, remoteProjectConfiguration).run()
  }

}
