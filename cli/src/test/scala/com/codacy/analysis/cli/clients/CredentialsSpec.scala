package com.codacy.analysis.cli.clients

import com.codacy.analysis.cli.command.APIOptions
import com.codacy.analysis.cli.configuration.Environment
import com.codacy.analysis.core.clients._
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class CredentialsSpec
    extends Specification
    with NoLanguageFeatures
    with Mockito
    with FutureMatchers {

  private val environment = new Environment(Map.empty)
  private val apiTokenStr = "RandomApiToken"
  private val projectTokenStr = "RandomProjectToken"
  private val username = "some_user"
  private val project = "some_project"

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

        val apiOptions = APIOptions(
          projectToken = Option(projectTokenStr),
          codacyApiBaseUrl = Option("codacy.com"))
        val credentials = Credentials.get(environment, apiOptions)

        credentials must beSome[Credentials]
        credentials must beLike { case Some(token) => token must beAnInstanceOf[ProjectToken] }
      }
    }
  }

}
