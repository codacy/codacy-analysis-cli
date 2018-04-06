package com.codacy.analysis.cli.clients

import com.codacy.analysis.cli.clients.api.ProjectConfiguration
import scalaj.http.{Http, HttpResponse}
import io.circe.parser._
import io.circe.{Json, ParsingFailure}
import io.circe.generic.auto._
import org.log4s.Logger

class CodacyClient(apiUrl: Option[String] = None,
                   apiTokenOpt: Option[String] = None,
                   projectTokenOpt: Option[String] = None)(implicit logger: Logger) {

  private val remoteUrl = apiUrl.getOrElse("https://api.codacy.com") + "/2.0"
  private lazy val connectionTimeoutMs = 2000
  private lazy val readTimeoutMs = 5000

  def get(endpoint: String): Either[ParsingFailure, Json] = {
    val headers: Map[String, String] = Map("Content-Type" -> "application/json") ++
      apiTokenOpt.map(apiToken => "api_token" -> apiToken) ++
      projectTokenOpt.map(projectTokenOpt => "project_token" -> projectTokenOpt)

    val response: HttpResponse[String] =
      Http(s"$remoteUrl/$endpoint").headers(headers).timeout(connectionTimeoutMs, readTimeoutMs).asString

    parse(response.body)
  }

  def getProjectConfiguration: Either[String, ProjectConfiguration] = {
    getProjectConfigurationFrom("/v2/project/analysis/configuration")
  }

  def getProjectConfiguration(projectName: String, username: String): Either[String, ProjectConfiguration] = {
    getProjectConfigurationFrom(s"/v2/project/$username/$projectName/analysis/configuration")
  }

  private def getProjectConfigurationFrom(endpoint: String) = {
    get(endpoint) match {
      case Left(e) =>
        logger.error(e)(s"""Error getting config file from endpoint "$endpoint" """)
        Left(e.message)
      case Right(json) =>
        parseProjectConfigResponse(json)
    }
  }

  private def parseProjectConfigResponse(json: Json): Either[String, ProjectConfiguration] =
    json.as[ProjectConfiguration] match {
      case Left(e) =>
        logger.error(e)("Error parsing config file")
        Left(e.message)
      case Right(p) => Right(p)
    }
}
