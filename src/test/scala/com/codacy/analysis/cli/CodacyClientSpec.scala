package com.codacy.analysis.cli

import com.codacy.analysis.cli.clients._
import com.codacy.analysis.cli.command.APIOptions
import com.codacy.analysis.cli.configuration.Environment
import com.codacy.analysis.cli.utils.HttpHelper
import com.codacy.analysis.cli.utils.TestUtils._
import io.circe.parser.parse
import io.circe.{Json, ParsingFailure}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.duration._

class CodacyClientSpec extends Specification with NoLanguageFeatures with Mockito with FutureMatchers {

  val apiTokenStr = "RandomApiToken"
  val projectTokenStr = "RandomProjectToken"
  val username = "some_user"
  val project = "some_project"
  val commitUuid = "some_commitUuid"
  val remoteUrl = "codacy.com/2.0"
  val tool = "eslint"
  val environment = new Environment(Map.empty)
  val apiCredentials: Credentials = APIToken(apiTokenStr, Some(remoteUrl), UserName(username), ProjectName(project))
  val projectCredentials: Credentials = ProjectToken(projectTokenStr, Some(remoteUrl))

  "Credentials" should {

    "Initialize credentials" in {
      "with API Token" in {
        "with user and project name" in {
          val apiOptions = APIOptions(
            apiToken = Option(apiTokenStr),
            username = Option(UserName(username)),
            project = Option(ProjectName(project)),
            codacyApiBaseUrl = Option("codacy.com"))
          val credentials: Option[Credentials] = Credentials.get(environment, apiOptions)

          credentials must beSome[Credentials]
          credentials must beLike { case Some(token) => token must beAnInstanceOf[APIToken] }
        }

        "without user and project name" in {
          val apiOptions = APIOptions(
            apiToken = Option(apiTokenStr),
            username = None,
            project = None,
            codacyApiBaseUrl = Option("codacy.com"))
          val credentials = Credentials.get(environment, apiOptions)

          credentials must beNone
        }
      }

      "with Project Token" in {

        val apiOptions = APIOptions(projectToken = Option(projectTokenStr), codacyApiBaseUrl = Option("codacy.com"))
        val credentials = Credentials.get(environment, apiOptions)

        credentials must beSome[Credentials]
        credentials must beLike { case Some(token) => token must beAnInstanceOf[ProjectToken] }
      }
    }
  }

  "CodacyClient" should {

    "Correctly set up endpoint call" in {

      "for sending results" in {
        "with API Token" in {
          "with successful return" in {
            val (codacyClient, httpHelper) = setupRemoteResultsTest(success = true, apiCredentials)
            // scalafix:off NoInfer.any
            codacyClient.sendRemoteResults(tool, commitUuid, Set()) must beRight.awaitFor(Int.MaxValue.seconds)
            // scalafix:on NoInfer.any NoInfer.any
            there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
          }
          "with unsuccessful return" in {
            val (codacyClient, httpHelper) = setupRemoteResultsTest(success = false, apiCredentials)
            // scalafix:off NoInfer.any
            val remoteResults = codacyClient.sendRemoteResults(tool, commitUuid, Set())
            remoteResults must beLeft.awaitFor(Int.MaxValue.seconds)
            // scalafix:on NoInfer.any
            remoteResults must beLike[Either[String, Unit]] {
              case Left(errorMsg) =>
                errorMsg mustEqual "Error: Endpoint for sending results replied with an error : failed!"
            }.awaitFor(Int.MaxValue.seconds)
            there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
          }
        }
        "with Project Token with successful return" in {
          val (codacyClient, httpHelper) = setupRemoteResultsTest(success = true, projectCredentials)
          // scalafix:off NoInfer.any
          codacyClient.sendRemoteResults(tool, commitUuid, Set()) must beRight.awaitFor(Int.MaxValue.seconds)
          // scalafix:on NoInfer.any
          there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
        }
      }

      "for sending end of results" in {
        "with API Token" in {
          "with successful return" in {
            val (codacyClient, httpHelper) = setupRemoteResultsEndTest(success = true, apiCredentials)
            // scalafix:off NoInfer.any
            codacyClient.sendEndOfResults(commitUuid) must beRight.awaitFor(Int.MaxValue.seconds)
            // scalafix:on NoInfer.any
            there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
          }
          "with unsuccessful return" in {
            val (codacyClient, httpHelper) = setupRemoteResultsEndTest(success = false, apiCredentials)
            // scalafix:off NoInfer.any
            val endOfResults = codacyClient.sendEndOfResults(commitUuid)
            endOfResults must beLeft.awaitFor(Int.MaxValue.seconds)
            // scalafix:on NoInfer.any
            endOfResults must beLike[Either[String, Unit]] {
              case Left(errorMsg) =>
                errorMsg mustEqual "Error: Endpoint for end of results replied with an error : failed!"
            }.awaitFor(Int.MaxValue.seconds)
            there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
          }
        }
        "with Project Token with successful return" in {
          val (codacyClient, httpHelper) = setupRemoteResultsEndTest(success = true, projectCredentials)
          // scalafix:off NoInfer.any
          codacyClient.sendEndOfResults(commitUuid) must beRight.awaitFor(Int.MaxValue.seconds)
          // scalafix:on NoInfer.any
          there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
        }
      }

      "for getting remote configuration" in {
        "with API Token" in {
          "with successful return" in {
            val (codacyClient, httpHelper) = setupGetRemoteConfigurationTest(success = true, apiCredentials)
            codacyClient.getRemoteConfiguration must beRight
            there was one(httpHelper).get(ArgumentMatchers.any[String])
          }
          "with unsuccessful return" in {
            val (codacyClient, httpHelper) = setupGetRemoteConfigurationTest(success = false, apiCredentials)
            val remoteConfig = codacyClient.getRemoteConfiguration
            remoteConfig must beLeft
            remoteConfig must beLike {
              case Left(errorMsg) =>
                errorMsg mustEqual "Error: getting Project Configuration : failed!"
            }
            there was one(httpHelper).get(ArgumentMatchers.any[String])
          }
        }
        "with Project Token with successful return" in {
          val (codacyClient, httpHelper) = setupGetRemoteConfigurationTest(success = true, projectCredentials)
          codacyClient.getRemoteConfiguration must beRight
          there was one(httpHelper).get(ArgumentMatchers.any[String])
        }
      }
    }
  }

  private def setupRemoteResultsTest(success: Boolean, credentials: Credentials): (CodacyClient, HttpHelper) = {

    val mockedHttpHelper = mock[HttpHelper]

    val response: Either[ParsingFailure, Json] =
      if (success) parse("""{ "success": "Results received successfully."}""")
      else parse("""{ "error": "failed!"}""")

    when(mockedHttpHelper.post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]]))
      .thenAnswer((invocation: InvocationOnMock) => {
        invocation.getArguments.toList match {
          case (endpoint: String) :: Nil =>
            val actualEndpoint = credentials match {
              case _: ProjectToken => s"/commit/$commitUuid/remoteResults"
              case _: APIToken     => s"/$username/$project/commit/$commitUuid/remoteResults"
            }
            endpoint must beEqualTo(actualEndpoint)
          case _ =>
        }
        response
      })

    (new CodacyClient(credentials, mockedHttpHelper), mockedHttpHelper)
  }

  private def setupRemoteResultsEndTest(success: Boolean, credentials: Credentials): (CodacyClient, HttpHelper) = {

    val mockedHttpHelper = mock[HttpHelper]

    val response: Either[ParsingFailure, Json] =
      if (success) parse("""{ "success": "Results received successfully."}""")
      else parse("""{ "error": "failed!"}""")

    when(mockedHttpHelper.post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]]))
      .thenAnswer((invocation: InvocationOnMock) => {
        invocation.getArguments.toList match {
          case (endpoint: String) :: Nil =>
            val actualEndpoint = credentials match {
              case _: ProjectToken => s"/commit/$commitUuid/resultsFinal"
              case _: APIToken     => s"/$username/$project/commit/$commitUuid/resultsFinal"
            }
            endpoint must beEqualTo(actualEndpoint)
          case _ =>
        }
        response
      })

    (new CodacyClient(credentials, mockedHttpHelper), mockedHttpHelper)
  }

  private def setupGetRemoteConfigurationTest(success: Boolean,
                                              credentials: Credentials): (CodacyClient, HttpHelper) = {

    val mockedHttpHelper = mock[HttpHelper]
    val response: Either[ParsingFailure, Json] =
      if (success)
        parse("""{ "ignoredPaths": [], "defaultIgnores": [], "projectExtensions": [], "toolConfiguration": [] }""")
      else parse("""{ "error": "failed!"}""")

    when(mockedHttpHelper.get(ArgumentMatchers.any[String])).thenAnswer((invocation: InvocationOnMock) => {
      invocation.getArguments.toList match {
        case (endpoint: String) :: Nil =>
          val actualEndpoint = credentials match {
            case _: ProjectToken => "/project/analysis/configuration"
            case _: APIToken     => s"/project/$username/$project/analysis/configuration"
          }
          endpoint must beEqualTo(actualEndpoint)
        case _ =>
      }
      response
    })

    (new CodacyClient(credentials, mockedHttpHelper), mockedHttpHelper)
  }

}
