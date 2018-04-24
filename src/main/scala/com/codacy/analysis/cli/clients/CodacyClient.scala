package com.codacy.analysis.cli.clients

import com.codacy.analysis.cli.clients.api.ProjectConfiguration
import com.codacy.api.dtos.{Language, Languages}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.{Decoder, Json, ParsingFailure}
import org.log4s.{Logger, getLogger}
import scalaj.http.{Http, HttpResponse}

class CodacyClient(apiUrl: Option[String] = None, extraHeaders: Map[String, String]) {

  private val logger: Logger = getLogger

  private val remoteUrl = apiUrl.getOrElse("https://api.codacy.com") + "/2.0"
  private lazy val connectionTimeoutMs = 2000
  private lazy val readTimeoutMs = 5000
  private implicit val fileEncoder: Decoder[Language] =
    Decoder[String].emap(lang =>
      Languages.fromName(lang).fold[Either[String, Language]](Left(s"Failed to parse language $lang"))(Right(_)))

  private def get(endpoint: String): Either[ParsingFailure, Json] = {
    val headers: Map[String, String] = Map("Content-Type" -> "application/json") ++ extraHeaders

    val response: HttpResponse[String] =
      Http(s"$remoteUrl$endpoint").headers(headers).timeout(connectionTimeoutMs, readTimeoutMs).asString

    parse(response.body)
  }

  def getProjectConfiguration: Either[String, ProjectConfiguration] = {
    getProjectConfigurationFrom("/project/analysis/configuration")
  }

  def getProjectConfiguration(projectName: String, username: String): Either[String, ProjectConfiguration] = {
    getProjectConfigurationFrom(s"/project/$username/$projectName/analysis/configuration")
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

object CodacyClient {

  def apply(credentials: Credentials): CodacyClient = {
    credentials match {
      case ProjectToken(token, baseUrl) =>
        val headers: Map[String, String] = Map(("project_token", token))
        new CodacyClient(baseUrl, headers)
      case APIToken(token, baseUrl) =>
        val headers: Map[String, String] = Map(("api_token", token))
        new CodacyClient(baseUrl, headers)
    }
  }

}
