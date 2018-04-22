package com.codacy.analysis.cli

import java.nio.file.{Path, Paths}

import better.files.File
import codacy.docker.api
import com.codacy.analysis.cli.analysis.Analyser
import com.codacy.analysis.cli.clients.api._
import com.codacy.analysis.cli.clients.{CodacyClient, ProjectToken}
import com.codacy.analysis.cli.command._
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.configuration.RemoteConfigurationFetcher
import com.codacy.analysis.cli.files.FileCollector
import com.codacy.analysis.cli.formatter.{Formatter, Json}
import com.codacy.analysis.cli.model.{Issue, Result}
import com.codacy.api.dtos.Languages.Python
import io.circe.generic.auto._
import io.circe.{Decoder, parser}
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

import scala.sys.process.Process
import scala.util.Try

class AnalyseExecutorSpec extends Specification with NoLanguageFeatures {
  implicit val categoryDencoder: Decoder[api.Pattern.Category.Value] = Decoder.enumDecoder(api.Pattern.Category)
  implicit val levelDencoder: Decoder[api.Result.Level.Value] = Decoder.enumDecoder(api.Result.Level)
  implicit val fileDencoder: Decoder[Path] = Decoder[String].map(Paths.get(_))

  "AnalyseExecutor" should {

    val pyLintPatternsInternalIds = Set("PyLint_C0111", "PyLint_E1101")
    val pathToIgnore = "lib/improver/tests/"
    s"""analyse a python project with pylint using a remote project configuration
      | that ignores the files that start with the path $pathToIgnore
      | and considers just patterns ${pyLintPatternsInternalIds.mkString(", ")}""".stripMargin in {
      (for {
        directory <- File.temporaryDirectory()
        file <- File.temporaryFile()
      } yield {

        Process(Seq("git", "clone", "git://github.com/qamine-test/improver.git", directory.pathAsString)).!
        Process(Seq("git", "reset", "--hard", "f4c45e59cc9ec2c01b2545b6b49334694a29e0da"), directory.toJava).!

        val analyse = Analyse(
          options = CommonOptions(),
          api = APIOptions(None, None, None, None, Some("codacy.com")),
          tool = "pylint",
          directory = Some(directory),
          format = Json.name,
          output = Some(file),
          extras = ExtraOptions())
        val formatter: Formatter = Formatter(analyse.format, analyse.output)
        val analyser: Analyser[Try] = Analyser(analyse.extras.analyser)
        val fileCollector: FileCollector[Try] = FileCollector.defaultCollector()
        val toolPatterns = pyLintPatternsInternalIds.map { patternId =>
          ToolPattern(patternId, Set.empty)
        }

        val codacyClient = new CodacyClient(None, Map.empty) {
          override def getProjectConfiguration: Either[String, ProjectConfiguration] = {
            Right(
              ProjectConfiguration(
                Set(FilePath(pathToIgnore)),
                Set(LanguageExtensions(Python, Set(".py"))),
                Set(
                  ToolConfiguration(
                    "34225275-f79e-4b85-8126-c7512c987c0d",
                    isEnabled = true,
                    notEdited = true,
                    toolPatterns))))
          }
        }

        val remoteConfigurationFetcher =
          new RemoteConfigurationFetcher(ProjectToken("RandomProjectToken"), codacyClient, analyse)
        new AnalyseExecutor(analyse, formatter, analyser, fileCollector, remoteConfigurationFetcher).run()

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
      }).get()
    }
  }
}
