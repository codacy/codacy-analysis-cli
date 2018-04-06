package com.codacy.analysis.cli.clients

import com.codacy.analysis.cli.clients.api.ProjectConfiguration
import scalaj.http.{Http, HttpResponse}
import io.circe.parser._
import io.circe.Json
import io.circe.generic.auto._
class CodacyClient(apiUrl: Option[String] = None,
                   apiTokenOpt: Option[String] = None,
                   projectTokenOpt: Option[String] = None) {

  private val remoteUrl = apiUrl.getOrElse("https://api.codacy.com") + "/2.0"
  private lazy val connectionTimeoutMs = 2000
  private lazy val readTimeoutMs = 5000

  def get(endpoint: String): RequestResponse[Json] = {
    val headers: Map[String, String] = Map("Content-Type" -> "application/json") ++
      apiTokenOpt.map(apiToken => "api_token" -> apiToken) ++
      projectTokenOpt.map(projectTokenOpt => "project_token" -> projectTokenOpt)

    val response: HttpResponse[String] =
      Http(s"$remoteUrl/$endpoint").headers(headers).timeout(connectionTimeoutMs, readTimeoutMs).asString

    parse(response.body) match {
      case Right(json) => SuccessfulResponse(json)
      case Left(error) => FailedResponse(error.message)
    }
  }

  def getProjectConfiguration: Either[String, ProjectConfiguration]  = {
    val projectConfigResponse = get("/v2/project/analysis/configuration")
    projectConfigResponse match {
      case SuccessfulResponse(json) =>
        parseProjectConfigResponse(json)
      case f: FailedResponse =>
        //TODO: add logs
      Left(f.message)
    }
  }

  private def parseProjectConfigResponse(json: Json): Either[String, ProjectConfiguration] =
    json.as[ProjectConfiguration] match {
      case Left(e) =>
        //TODO: add logs
        Left(e.message)
      case Right(p) => Right(p)
    }
}

