package com.codacy.analysis.cli.upload

import better.files.File
import cats.implicits._
import com.codacy.analysis.cli.clients.CodacyClient
import com.codacy.analysis.cli.clients.api._
import com.codacy.analysis.cli.model.Result
import com.codacy.analysis.cli.utils.TestUtils._
import io.circe.generic.auto._
import io.circe.parser
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.OngoingStubbing
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.{FutureMatchers, MatchResult}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.core.Fragment

import scala.concurrent.Future
import scala.concurrent.duration._

class ResultsUploaderSpec extends Specification with NoLanguageFeatures with Mockito with FutureMatchers {

  private val commitUuid = "9232dbdcae98b19412c8dd98c49da8c391612bfa"
  private val tool = "eslint"
  private val batchSize = 10

  "ResultsUploader" should {

    "not create the uploader if upload is not requested" in {
      val codacyClient = mock[CodacyClient]
      ResultsUploader(Option(codacyClient), upload = false, Option(commitUuid), Option(batchSize)) must beEqualTo(
        Right(Option.empty[ResultsUploader]))
    }

    "fail to create the uploader if the client is not passed" in {
      ResultsUploader(Option.empty[CodacyClient], upload = true, Option(commitUuid), Option(batchSize)) must beLeft(
        "No credentials found.")
    }

    "fail to create the uploader if the commitc is not passed" in {
      val codacyClient = mock[CodacyClient]
      ResultsUploader(Option(codacyClient), upload = true, Option.empty[String], Option(batchSize)) must beLeft(
        "No commit found.")
    }

    val exampleResultsEither = for {
      resultsJson <- parser.parse(
        File.resource("com/codacy/analysis/cli/upload/cli-output-monogatari-eslint-1.json").contentAsString)
      exampleResults <- resultsJson.as[Set[Result]]
    } yield exampleResults

    val exampleResults = exampleResultsEither.right.get
    testBatchSize(exampleResults)(-10, "sending batches of -10 results in each payload - should use default")
    testBatchSize(exampleResults)(0, "sending batches of 0 results in each payload - should use default")
    testBatchSize(exampleResults)(100, "sending batches of 100 results in each payload - should use 100")
    testBatchSize(exampleResults)(
      5000,
      "sending batches of 5000 (> results.length) results in each payload - should use results.length")

  }

  private def testBatchSize(exampleResults: Set[Result])(batchSize: Int, message: String): Fragment = {
    val esLintPatternsInternalIds = Set("ESLint_semi", "ESLint_no-undef", "ESLint_indent", "ESLint_no-empty")

    s"analyse a javascript project with eslint, $message".stripMargin in {
      val toolPatterns = esLintPatternsInternalIds.map { patternId =>
        ToolPattern(patternId, Set.empty)
      }
      val codacyClient = mock[CodacyClient]
      val uploader: ResultsUploader =
        ResultsUploader(Option(codacyClient), upload = true, Option(commitUuid), Option(batchSize)).right.get.get
      val actualBatchSize = if (batchSize > 0) batchSize else uploader.defaultBatchSize

      when(codacyClient.getRemoteConfiguration).thenReturn(getMockedRemoteConfiguration(toolPatterns))

      verifyBatchSize(codacyClient, tool, commitUuid, actualBatchSize)

      when(codacyClient.sendEndOfResults(commitUuid)).thenReturn(Future(().asRight[String]))

      // scalafix:off NoInfer.any
      uploader.sendResults(tool, exampleResults) must beRight.awaitFor(10.minutes)
      // scalafix:on NoInfer.any

      verifyNumberOfCalls(exampleResults)(codacyClient, tool, commitUuid, actualBatchSize)
    }
  }

  private def getMockedRemoteConfiguration(toolPatterns: Set[ToolPattern]): Either[String, ProjectConfiguration] = {
    ProjectConfiguration(
      Set.empty,
      Some(Set.empty),
      Set.empty,
      Set(ToolConfiguration("cf05f3aa-fd23-4586-8cce-5368917ec3e5", isEnabled = true, notEdited = false, toolPatterns)))
      .asRight[String]
  }

  private def verifyBatchSize(codacyClient: CodacyClient,
                              tool: String,
                              commitUuid: String,
                              batchSize: Int): OngoingStubbing[Future[Either[String, Unit]]] = {
    when(codacyClient
      .sendRemoteResults(ArgumentMatchers.eq(tool), ArgumentMatchers.eq(commitUuid), ArgumentMatchers.any[Set[Result]]))
      .thenAnswer((invocation: InvocationOnMock) => {
        val a = invocation.getArguments()(2).asInstanceOf[Set[Result]]
        a.size must beLessThanOrEqualTo(batchSize)
        Future.successful(().asRight[String])
      })
  }

  private def verifyNumberOfCalls(exampleResults: Set[Result])(
    codacyClient: CodacyClient,
    tool: String,
    commitUuid: String,
    batchSize: Int): MatchResult[Future[Either[String, Unit]]] = {
    val numBatchesDouble: Double = exampleResults.size.toDouble / batchSize.toDouble
    val numBatches = Math.ceil(numBatchesDouble).toInt

    there was numBatches
      .times(codacyClient)
      .sendRemoteResults(ArgumentMatchers.eq(tool), ArgumentMatchers.eq(commitUuid), ArgumentMatchers.any[Set[Result]])

    there was one(codacyClient).sendEndOfResults(ArgumentMatchers.eq(commitUuid))
  }

}
