package com.codacy.analysis.cli.rules

import java.net.URL
import cats.implicits._
import scala.util.Try

class AnalyseRules(environmentVariables: Map[String, String]) {

  private val publicApiBaseUrl = "https://api.codacy.com"

  def validateProjectToken(projectTokenOpt: Option[String]): Either[String, String] = {
    for {
      projectToken <- projectTokenOpt.fold(getProjectToken)(_.asRight)
      validatedProjectToken <- if (projectToken.trim.isEmpty) {
        "Empty argument for --project-token".asLeft
      } else {
        projectToken.asRight
      }
    } yield {
      validatedProjectToken
    }
  }

  def validateApiBaseUrl(codacyApiBaseURL: Option[String]): Either[String, String] = {
    val apiURL = codacyApiBaseURL.getOrElse(getApiBaseUrl)

    Try(new URL(apiURL)).toEither match {
      case Left(_) =>
        val error = s"Invalid codacy api url: $apiURL"

        val help = if (!apiURL.startsWith("http")) {
          "Maybe you forgot the http:// or https:// ?"
        }
        s"$error\n$help".asLeft
      case Right(_) => apiURL.asRight
    }
  }

  private def getProjectToken: Either[String, String] = {
    Either.fromOption(
      environmentVariables.get("CODACY_PROJECT_TOKEN"),
      """Project token not provided and not available in environment variable "CODACY_PROJECT_TOKEN"""")
  }

  private def getApiBaseUrl: String = {
    environmentVariables.getOrElse("CODACY_API_BASE_URL", publicApiBaseUrl)
  }
}
