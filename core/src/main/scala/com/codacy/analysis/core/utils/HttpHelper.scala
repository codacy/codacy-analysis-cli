package com.codacy.analysis.core.utils

import io.circe.parser.parse
import io.circe.{Json, ParsingFailure}
import org.log4s.{Logger, getLogger}
import scalaj.http.{Http, HttpOptions, HttpRequest, HttpResponse}

class HttpHelper(apiUrl: String, extraHeaders: Map[String, String], allowUnsafeSSL: Boolean) {

  private lazy val connectionTimeoutMs = 5000
  private lazy val readTimeoutMs = 15000

  private val remoteUrl = apiUrl + "/2.0"

  private val logger: Logger = getLogger

  private def httpOptions = if (allowUnsafeSSL) Seq(HttpOptions.allowUnsafeSSL) else Seq.empty

  def get(endpoint: String): Either[ParsingFailure, Json] = {
    val headers: Map[String, String] = Map("Content-Type" -> "application/json") ++ extraHeaders

    val response: HttpResponse[String] =
      Http(s"$remoteUrl$endpoint")
        .headers(headers)
        .timeout(connectionTimeoutMs, readTimeoutMs)
        .options(httpOptions)
        .asString

    parse(response.body)

  }

  def post(endpoint: String, dataOpt: Option[Json] = None): Either[ParsingFailure, Json] = {
    val headers: Map[String, String] = dataOpt.fold(Map.empty[String, String]) { _ =>
      Map("Content-Type" -> "application/json")
    } ++ extraHeaders

    val request: HttpRequest = dataOpt.map { data =>
      Http(s"$remoteUrl$endpoint").options(httpOptions).postData(data.toString)

    }.getOrElse(Http(s"$remoteUrl$endpoint"))
      .method("POST")
      .headers(headers)
      .timeout(connectionTimeoutMs, readTimeoutMs)

    val response = request.asString
    val bodyAsString = response.body
    parse(bodyAsString) match {
      case failure @ Left(_) =>
        logger.warn(s"Post to $endpoint failed. Response was a ${response.code} and returned $bodyAsString")
        failure
      case success => success
    }
  }

}
