package com.codacy.analysis.cli

import com.codacy.analysis.cli.clients.{APIToken, CodacyClient, Credentials, ProjectToken}
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

class CodacyClientSpec extends Specification with NoLanguageFeatures with Mockito with FutureMatchers {

  val apiTokenStr = "RandomApiToken"
  val projectTokenStr = "RandomProjectToken"
  val username = "some_user"
  val project = "some_project"
  val commitUuid = "some_commitUuid"
  val remoteUrl = "codacy.com/2.0"
  val tool = "eslint"
  val environment = new Environment(sys.env)
  val apiCredentials: Credentials = APIToken(apiTokenStr, Some(remoteUrl), username, project)
  val projectCredentials: Credentials = ProjectToken(projectTokenStr, Some(remoteUrl))

  "Credentials" should {

    "Initialize credentials" in {
      "with API Token" in {
        "with user and project name" in {
          val apiOptions = APIOptions(
            apiToken = Option(apiTokenStr),
            username = Option(username),
            project = Option(project),
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
            codacyClient.sendRemoteResults(tool, commitUuid, Set()) must beRight.await
            there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
          }
          "with unsuccessful return" in {
            val (codacyClient, httpHelper) = setupRemoteResultsTest(success = false, apiCredentials)
            codacyClient.sendRemoteResults(tool, commitUuid, Set()) must beLeft.await
            there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
          }
        }
        "with Project Token with successful return" in {
          val (codacyClient, httpHelper) = setupRemoteResultsTest(success = true, projectCredentials)
          codacyClient.sendRemoteResults(tool, commitUuid, Set()) must beRight.await
          there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
        }
      }

      "for sending end of results" in {
        "with API Token" in {
          "with successful return" in {
            val (codacyClient, httpHelper) = setupRemoteResultsEndTest(success= true, apiCredentials)
            codacyClient.sendEndOfResults(commitUuid) must beRight.await
            there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
          }
          "with unsuccessful return" in {
            val (codacyClient, httpHelper) = setupRemoteResultsEndTest(success= false, apiCredentials)
            codacyClient.sendEndOfResults(commitUuid) must beLeft.await
            there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
          }
        }
        "with Project Token with successful return" in {
          val (codacyClient, httpHelper) = setupRemoteResultsEndTest(success= true, projectCredentials)
          codacyClient.sendEndOfResults(commitUuid) must beRight.await
          there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
        }
      }

      "for getting remote configuration" in {
        "with API Token" in {
          "with successful return" in {
            val (codacyClient, httpHelper) = setupGetRemoteConfigurationTest(success= true, apiCredentials)
            codacyClient.getRemoteConfiguration must beRight
            there was one(httpHelper).get(ArgumentMatchers.any[String])
          }
          "with unsuccessful return" in {
            val (codacyClient, httpHelper) = setupGetRemoteConfigurationTest(success= false, apiCredentials)
            codacyClient.getRemoteConfiguration must beLeft
            there was one(httpHelper).get(ArgumentMatchers.any[String])
          }
        }
        "with Project Token with successful return" in {
          val (codacyClient, httpHelper) = setupGetRemoteConfigurationTest(success= true, projectCredentials)
          codacyClient.getRemoteConfiguration must beRight
          there was one(httpHelper).get(ArgumentMatchers.any[String])
        }
      }

    }

//    "Correctly set up endpoint call for sending results with API Token with successful return" in {
//
//      val (codacyClient, httpHelper) = setupRemoteResultsTest(success= true, apiCredentials)
//      codacyClient.sendRemoteResults(tool, commitUuid, Set()) must beRight.await
//      there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
//
//    }
//
//    "Correctly set up endpoint call for sending results with API Token with unsuccessful return" in {
//
//      val (codacyClient, httpHelper) = setupRemoteResultsTest(success= false, apiCredentials)
//      codacyClient.sendRemoteResults(tool, commitUuid, Set()) must beLeft.await
//      there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
//
//    }
//
//    "Correctly set up endpoint call for sending results with Project Token with successful return" in {
//
//      val (codacyClient, httpHelper) = setupRemoteResultsTest(success= true, projectCredentials)
//      codacyClient.sendRemoteResults(tool, commitUuid, Set()) must beRight.await
//      there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
//
//    }

//    "Correctly set up endpoint call for sending end of results with API Token with successful return" in {
//
//      val (codacyClient, httpHelper) = setupRemoteResultsEndTest(success= true, apiCredentials)
//      codacyClient.sendEndOfResults(commitUuid) must beRight.await
//      there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
//
//    }
//
//    "Correctly set up endpoint call for sending end of results with API Token with unsuccessful return" in {
//
//      val (codacyClient, httpHelper) = setupRemoteResultsEndTest(success= false, apiCredentials)
//      codacyClient.sendEndOfResults(commitUuid) must beLeft.await
//      there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
//
//    }
//
//    "Correctly set up endpoint call for sending end of results with Project Token with successful return" in {
//
//      val (codacyClient, httpHelper) = setupRemoteResultsEndTest(success= true, projectCredentials)
//      codacyClient.sendEndOfResults(commitUuid) must beRight.await
//      there was one(httpHelper).post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]])
//
//    }

//    "Correctly set up endpoint call for getting remote configuration with API Token with successful return" in {
//
//      val (codacyClient, httpHelper) = setupGetRemoteConfigurationTest(success= true, apiCredentials)
//      codacyClient.getRemoteConfiguration must beRight
//      there was one(httpHelper).get(ArgumentMatchers.any[String])
//
//    }
//
//    "Correctly set up endpoint call for getting remote configuration with API Token with unsuccessful return" in {
//
//      val (codacyClient, httpHelper) = setupGetRemoteConfigurationTest(success= false, apiCredentials)
//      codacyClient.getRemoteConfiguration must beLeft
//      there was one(httpHelper).get(ArgumentMatchers.any[String])
//
//    }
//
//    "Correctly set up endpoint call for getting remote configuration with Project Token with successful return" in {
//
//      val (codacyClient, httpHelper) = setupGetRemoteConfigurationTest(success= true, projectCredentials)
//      codacyClient.getRemoteConfiguration must beRight
//      there was one(httpHelper).get(ArgumentMatchers.any[String])
//
//    }
  }

  private def setupRemoteResultsTest(success: Boolean,
                            credentials: Credentials): (CodacyClient, HttpHelper) = {

    val mockedHttpHelper = mock[HttpHelper]

    val response: Either[ParsingFailure, Json] =
      if(success) parse("""{ "success": "Results received successfully."}""")
      else parse("""{ "error": "failed."}""")

    when(mockedHttpHelper.post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]]))
      .thenAnswer((invocation: InvocationOnMock) => {
        invocation.getArguments.toList match {
          case (endpoint: String) :: Nil =>
            val actualEndpoint = credentials match {
              case _ : ProjectToken => s"/commit/$commitUuid/remoteResults"
              case _ : APIToken => s"/$username/$project/commit/$commitUuid/remoteResults"
            }
            endpoint must beEqualTo(actualEndpoint)
          case _ =>
        }
        response
      })

    (new CodacyClient(credentials, mockedHttpHelper), mockedHttpHelper)
  }

  private def setupRemoteResultsEndTest(success: Boolean,
                                     credentials: Credentials): (CodacyClient, HttpHelper) = {

    val mockedHttpHelper = mock[HttpHelper]

    val response: Either[ParsingFailure, Json] =
      if(success) parse("""{ "success": "Results received successfully."}""")
      else parse("""{ "error": "failed."}""")

    when(mockedHttpHelper.post(ArgumentMatchers.any[String], ArgumentMatchers.any[Option[Json]]))
      .thenAnswer((invocation: InvocationOnMock) => {
        invocation.getArguments.toList match {
          case (endpoint: String) :: Nil =>
            val actualEndpoint = credentials match {
              case _ : ProjectToken => s"/commit/$commitUuid/endRemoteResults"
              case _ : APIToken => s"/$username/$project/commit/$commitUuid/endRemoteResults"
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
      if(success)
        parse("""{ "ignoredPaths": [], "projectExtensions": [], "toolConfiguration": [] }""")
      else parse("""{ "error": "failed."}""")

    when(mockedHttpHelper.get(ArgumentMatchers.any[String])).thenAnswer((invocation: InvocationOnMock) => {
      invocation.getArguments.toList match {
        case (endpoint: String) :: Nil =>
          val actualEndpoint = credentials match {
            case _ : ProjectToken => "/project/analysis/configuration"
            case _ : APIToken => s"/project/$project/$username/analysis/configuration"
          }
          endpoint must beEqualTo(actualEndpoint)
        case _ =>
      }
      response
    })

    (new CodacyClient(credentials, mockedHttpHelper), mockedHttpHelper)
  }

}
