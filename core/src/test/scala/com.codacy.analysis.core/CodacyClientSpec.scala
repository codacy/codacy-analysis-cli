package com.codacy.analysis.core

import com.codacy.analysis.core.clients._
import com.codacy.analysis.core.git.Commit
import com.codacy.analysis.core.utils.HttpHelper
import io.circe.parser.parse
import io.circe.{Json, ParsingFailure}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.specs2.control.NoLanguageFeatures
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scalaz.zio.RTS

class CodacyClientSpec extends Specification with NoLanguageFeatures with Mockito with RTS {

  private val apiTokenStr = "RandomApiToken"
  private val projectTokenStr = "RandomProjectToken"
  private val username = "some_user"
  private val project = "some_project"
  private val commitUuid = Commit.Uuid("some_commitUuid")
  private val remoteUrl = "codacy.com/2.0"
  private val tool = "eslint"
  private val apiCredentials: Credentials =
    APIToken(apiTokenStr, Some(remoteUrl), UserName(username), ProjectName(project))
  private val projectCredentials: Credentials = ProjectToken(projectTokenStr, Some(remoteUrl))

  "CodacyClient" should {

    "Correctly set up endpoint call" in {

      "for sending results" in {
        "with API Token" in {
          "with successful return" in {
            val (codacyClient, httpHelper) = setupRemoteResultsTest(success = true, apiCredentials)
            // scalafix:off NoInfer.any
            unsafeRun(codacyClient.sendRemoteIssues(tool, commitUuid, Right(Set()))) must beRight
            // scalafix:on NoInfer.any NoInfer.any
            there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
          }
          "with unsuccessful return" in {
            val (codacyClient, httpHelper) = setupRemoteResultsTest(success = false, apiCredentials)
            // scalafix:off NoInfer.any
            val remoteResults = unsafeRun(codacyClient.sendRemoteIssues(tool, commitUuid, Right(Set())))
            remoteResults must beLeft
            // scalafix:on NoInfer.any
            remoteResults must beLike[Either[String, Unit]] {
              case Left(errorMsg) =>
                errorMsg mustEqual "Error: Endpoint for sending results replied with an error : failed!"
            }
            there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
          }
        }
        "with Project Token with successful return" in {
          val (codacyClient, httpHelper) = setupRemoteResultsTest(success = true, projectCredentials)
          // scalafix:off NoInfer.any
          unsafeRun(codacyClient.sendRemoteIssues(tool, commitUuid, Right(Set()))) must beRight
          // scalafix:on NoInfer.any
          there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
        }
      }

      "for sending end of results" in {
        "with API Token" in {
          "with successful return" in {
            val (codacyClient, httpHelper) = setupRemoteResultsEndTest(success = true, apiCredentials)
            // scalafix:off NoInfer.any
            unsafeRun(codacyClient.sendEndOfResults(commitUuid)) must beRight
            // scalafix:on NoInfer.any
            there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
          }
          "with unsuccessful return" in {
            val (codacyClient, httpHelper) = setupRemoteResultsEndTest(success = false, apiCredentials)
            // scalafix:off NoInfer.any
            val endOfResults = unsafeRun(codacyClient.sendEndOfResults(commitUuid))
            endOfResults must beLeft
            // scalafix:on NoInfer.any
            endOfResults must beLike[Either[String, Unit]] {
              case Left(errorMsg) =>
                errorMsg mustEqual "Error: Endpoint for end of results replied with an error : failed!"
            }
            there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
          }
        }
        "with Project Token with successful return" in {
          val (codacyClient, httpHelper) = setupRemoteResultsEndTest(success = true, projectCredentials)
          // scalafix:off NoInfer.any
          unsafeRun(codacyClient.sendEndOfResults(commitUuid)) must beRight
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
              case _: ProjectToken => s"/commit/${commitUuid.value}/remoteResults"
              case _: APIToken     => s"/$username/$project/commit/${commitUuid.value}/remoteResults"
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
              case _: ProjectToken => s"/commit/${commitUuid.value}/resultsFinal"
              case _: APIToken     => s"/$username/$project/commit/${commitUuid.value}/resultsFinal"
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
