package com.codacy.analysis.cli.utils

import io.circe.parser.parse
import io.circe.{Json, ParsingFailure}
import scalaj.http.{Http, HttpRequest, HttpResponse}

class HttpHelper(apiUrl: Option[String], extraHeaders: Map[String, String]) {

  private lazy val connectionTimeoutMs = 2000
  private lazy val readTimeoutMs = 5000

  private val remoteUrl = apiUrl.getOrElse("https://api.codacy.com") + "/2.0"

  def get(endpoint: String): Either[ParsingFailure, Json] = {
    val headers: Map[String, String] = Map("Content-Type" -> "application/json") ++ extraHeaders

    val response: HttpResponse[String] =
      Http(s"$remoteUrl$endpoint").headers(headers).timeout(connectionTimeoutMs, readTimeoutMs).asString

    parse(response.body)
  }

  def post(endpoint: String, dataOpt: Option[Json] = None): Either[ParsingFailure, Json] = {
    val headers: Map[String, String] = dataOpt.fold(Map.empty[String, String]) { _ =>
      Map("Content-Type" -> "application/json")
    } ++ extraHeaders

    val request: HttpRequest = dataOpt.map { data =>
      Http(s"$remoteUrl$endpoint").postData(data.toString)
    }.getOrElse(Http(s"$remoteUrl$endpoint"))
      .method("POST")
      .headers(headers)
      .timeout(connectionTimeoutMs, readTimeoutMs)

    parse(request.asString.body)
  }

}
