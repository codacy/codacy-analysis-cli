package com.codacy.analysis.cli

import com.codacy.analysis.cli.analysis.Analyser
import com.codacy.analysis.cli.clients.api._
import com.codacy.analysis.cli.clients.CodacyClient
import com.codacy.analysis.cli.command._
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.files.FileCollector
import com.codacy.analysis.cli.formatter.{Formatter, Json}
import com.codacy.analysis.cli.model.Result
import com.codacy.analysis.cli.upload.ResultsUploader
import com.codacy.analysis.cli.utils.TestUtils._
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.stubbing.Answer
import org.mockito.invocation.InvocationOnMock
import cats.implicits._
import io.circe.generic.auto._
import io.circe.parser

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class ResultsUploaderSpec extends Specification with NoLanguageFeatures {

  "ResultsUploader" should {

    testBatchSize(100)
    testBatchSize(5000)

  }

  private def testBatchSize(batchSize: Int) = {
    val esLintPatternsInternalIds = Set("ESLint_semi", "ESLint_no-undef", "ESLint_indent", "ESLint_no-empty")

    s"""analyse a javascript project with eslint, sending batches of $batchSize results in each payload""".stripMargin in {
      val commitUuid = "9232dbdcae98b19412c8dd98c49da8c391612bfa"
      withClonedRepo("git://github.com/qamine-test/Monogatari.git", commitUuid) { (file, directory) =>
        val apiTokenStr = "RandomApiToken"
        val username = "some_user"
        val project = "some_project"
        val tool = "eslint"


        val analyse = Analyse(
          options = CommonOptions(),
          api = APIOptions(
            apiToken = Option(apiTokenStr),
            username = Option(username),
            project = Option(project),
            codacyApiBaseUrl = Option("codacy.com")),
          tool = tool,
          directory = Option(directory),
          format = Json.name,
          output = Option(file),
          extras = ExtraOptions(),
          commit = Option(commitUuid))
        val toolPatterns = esLintPatternsInternalIds.map { patternId =>
          ToolPattern(patternId, Set.empty)
        }

        val codacyClient = mock(classOf[CodacyClient])

        when(codacyClient.getRemoteConfiguration).thenReturn(
          Right(
            ProjectConfiguration(
              Set.empty,
              Set.empty,
              Set(
                ToolConfiguration(
                  "cf05f3aa-fd23-4586-8cce-5368917ec3e5",
                  isEnabled = true,
                  notEdited = false,
                  toolPatterns)))))

        when(
          codacyClient.sendRemoteResults(
            ArgumentMatchers.eq(tool),
            ArgumentMatchers.eq(commitUuid),
            ArgumentMatchers.any[Seq[Result]])).thenAnswer(new Answer[Future[Either[String, Unit]]] {
          def answer(invocation: InvocationOnMock): Future[Either[String, Unit]] = {
            val a = invocation.getArguments()(2).asInstanceOf[Seq[Result]]
            a.length must beLessThanOrEqualTo(batchSize)
            Future(().asRight[String])
          }
        })

        when(codacyClient.sendEndOfResults(commitUuid)).thenReturn(Future(().asRight[String]))

        val uploader: ResultsUploader = new ResultsUploader(commitUuid, codacyClient, Some(batchSize))
        runAnalyseExecutor(analyse, codacyClient.getRemoteConfiguration, uploader.asRight[String])

        val result = for {
          responseJson <- parser.parse(file.contentAsString)
          response <- responseJson.as[Set[Result]]
        } yield response

        result.map { response =>
          val numBatchesDouble: Double = response.size.toDouble / batchSize.toDouble
          val numBatches = Math.ceil(numBatchesDouble).toInt
          verify(codacyClient, times(numBatches)).sendRemoteResults(
            ArgumentMatchers.eq(tool),
            ArgumentMatchers.eq(commitUuid),
            ArgumentMatchers.any[Seq[Result]])

          verify(codacyClient, times(1)).sendEndOfResults(commitUuid)
        }

        true must beTrue

      }
    }
  }

  private def runAnalyseExecutor(analyse: Analyse,
                                 remoteProjectConfiguration: Either[String, ProjectConfiguration],
                                 resultsUploaderEither: Either[String, ResultsUploader]): Unit = {
    val formatter: Formatter = Formatter(analyse.format, analyse.output)
    val analyser: Analyser[Try] = Analyser(analyse.extras.analyser)
    val fileCollector: FileCollector[Try] = FileCollector.defaultCollector()

    new AnalyseExecutor(analyse, formatter, analyser, resultsUploaderEither, fileCollector, remoteProjectConfiguration)
      .run()
  }

}
