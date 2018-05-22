package com.codacy.analysis.cli.upload

import better.files.File
import caseapp.Tag
import cats.implicits._
import com.codacy.analysis.cli.analysis.Analyser
import com.codacy.analysis.cli.clients.{CodacyClient, ProjectName, UserName}
import com.codacy.analysis.cli.clients.api._
import com.codacy.analysis.cli.command._
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.files.FileCollector
import com.codacy.analysis.cli.formatter.{Formatter, Json}
import com.codacy.analysis.cli.model.Result
import com.codacy.analysis.cli.utils.TestUtils._
import io.circe
import io.circe.generic.auto._
import io.circe.parser
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.{FutureMatchers, MatchResult}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.Future
import scala.util.Try

class ResultsUploaderSpec extends Specification with NoLanguageFeatures with Mockito with FutureMatchers {

  "ResultsUploader" should {

    testBatchSize(-10, "sending batches of -10 results in each payload - should use default")
    testBatchSize(0, "sending batches of 0 results in each payload - should use default")
    testBatchSize(100, "sending batches of 100 results in each payload - should use 100")
    testBatchSize(
      5000,
      "sending batches of 5000 (> results.length) results in each payload - should use results.length")

  }

  private def testBatchSize(batchSize: Int, message: String) = {
    val esLintPatternsInternalIds = Set("ESLint_semi", "ESLint_no-undef", "ESLint_indent", "ESLint_no-empty")

    s"analyse a javascript project with eslint, $message".stripMargin in {
      val commitUuid = "9232dbdcae98b19412c8dd98c49da8c391612bfa"
      withClonedRepo("git://github.com/qamine-test/Monogatari.git", commitUuid) { (file, directory) =>
        val apiTokenStr = "RandomApiToken"
        val username = "some_user"
        val project = "some_project"
        val tool = "eslint"
        val analyse = Analyse(
          options = CommonOptions(Tag.of(1)),
          api = APIOptions(
            apiToken = Option(apiTokenStr),
            username = Option(UserName(username)),
            project = Option(ProjectName(project)),
            codacyApiBaseUrl = Option("codacy.com")),
          tool = Option(tool),
          directory = Option(directory),
          format = Json.name,
          output = Option(file),
          extras = ExtraOptions(),
          commitUuid = Option(commitUuid))
        val toolPatterns = esLintPatternsInternalIds.map { patternId =>
          ToolPattern(patternId, Set.empty)
        }
        val codacyClient = mock[CodacyClient]
        val uploader: ResultsUploader = new ResultsUploader(commitUuid, codacyClient, Some(batchSize))
        val actualBatchSize = if (batchSize > 0) batchSize else uploader.defaultBatchSize

        when(codacyClient.getRemoteConfiguration).thenReturn(getMockedRemoteConfiguration(toolPatterns))

        verifyBatchSize(codacyClient, tool, commitUuid, actualBatchSize)

        when(codacyClient.sendEndOfResults(commitUuid)).thenReturn(Future(().asRight[String]))

        runAnalyseExecutor(analyse, codacyClient.getRemoteConfiguration) must beRight

        verifyNumberOfCalls(codacyClient, tool, commitUuid, actualBatchSize, file)
      }
    }
  }

  private def getMockedRemoteConfiguration(toolPatterns: Set[ToolPattern]): Either[String, ProjectConfiguration] = {
    ProjectConfiguration(
      Set.empty,
      Set.empty,
      Set(ToolConfiguration("cf05f3aa-fd23-4586-8cce-5368917ec3e5", isEnabled = true, notEdited = false, toolPatterns)))
      .asRight[String]
  }

  private def verifyBatchSize(codacyClient: CodacyClient, tool: String, commitUuid: String, batchSize: Int) = {
    when(codacyClient
      .sendRemoteResults(ArgumentMatchers.eq(tool), ArgumentMatchers.eq(commitUuid), ArgumentMatchers.any[Set[Result]]))
      .thenAnswer((invocation: InvocationOnMock) => {
        val a = invocation.getArguments()(2).asInstanceOf[Set[Result]]
        a.size must beLessThanOrEqualTo(batchSize)
        Future.successful(().asRight[String])
      })
  }

  private def verifyNumberOfCalls(
    codacyClient: CodacyClient,
    tool: String,
    commitUuid: String,
    batchSize: Int,
    file: File): MatchResult[Either[circe.Error, MatchResult[Future[Either[String, Unit]]]]] = {
    val result = for {
      responseJson <- parser.parse(file.contentAsString)
      response <- responseJson.as[Set[Result]]
    } yield response

    result.map { response =>
      val numBatchesDouble: Double = response.size.toDouble / batchSize.toDouble
      val numBatches = Math.ceil(numBatchesDouble).toInt

      there was numBatches
        .times(codacyClient)
        .sendRemoteResults(
          ArgumentMatchers.eq(tool),
          ArgumentMatchers.eq(commitUuid),
          ArgumentMatchers.any[Set[Result]])

      there was one(codacyClient).sendEndOfResults(commitUuid)
    } must beRight
  }

  private def runAnalyseExecutor(analyse: Analyse, remoteProjectConfiguration: Either[String, ProjectConfiguration])
    : Either[String, Seq[AnalyseExecutor.ExecutorResult]] = {
    val formatter: Formatter = Formatter(analyse.format, analyse.output)
    val analyser: Analyser[Try] = Analyser(analyse.extras.analyser)
    val fileCollector: FileCollector[Try] = FileCollector.defaultCollector()

    new AnalyseExecutor(
      analyse.tool,
      analyse.directory,
      formatter,
      analyser,
      fileCollector,
      remoteProjectConfiguration,
      None).run()
  }

}
