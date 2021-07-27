package com.codacy.analysis.core.utils

import io.circe.parser.parse
import io.circe.{Json, ParsingFailure}
import scalaj.http.{Http, HttpRequest, HttpResponse, HttpOptions}

class HttpHelper(apiUrl: String, extraHeaders: Map[String, String], allowUnsafeSSL: Boolean = false) {

  private lazy val connectionTimeoutMs = 2000
  private lazy val readTimeoutMs = 5000

  private val remoteUrl = apiUrl + "/2.0"

  def get(endpoint: String): Either[ParsingFailure, Json] = {
    val headers: Map[String, String] = Map("Content-Type" -> "application/json") ++ extraHeaders

    var response: Option[HttpResponse[String]] = Option(null);
    if (allowUnsafeSSL) {
      response = Option(
        Http(s"$remoteUrl$endpoint")
          .headers(headers)
          .timeout(connectionTimeoutMs, readTimeoutMs)
          .options(HttpOptions.allowUnsafeSSL)
          .asString)
    } else {
      response = Option(
        Http(s"$remoteUrl$endpoint").headers(headers).timeout(connectionTimeoutMs, readTimeoutMs).asString)
    }

    parse(response.get.body)
  }

  def post(endpoint: String, dataOpt: Option[Json] = None): Either[ParsingFailure, Json] = {
    val headers: Map[String, String] = dataOpt.fold(Map.empty[String, String]) { _ =>
      Map("Content-Type" -> "application/json")
    } ++ extraHeaders

    val request: HttpRequest = dataOpt.map { data =>
      if (allowUnsafeSSL) {
        Http(s"$remoteUrl$endpoint").options(HttpOptions.allowUnsafeSSL).postData(data.toString)
      } else {
        Http(s"$remoteUrl$endpoint").postData(data.toString)
      }

    }.getOrElse(Http(s"$remoteUrl$endpoint"))
      .method("POST")
      .headers(headers)
      .timeout(connectionTimeoutMs, readTimeoutMs)

    parse(request.asString.body)
  }

}
