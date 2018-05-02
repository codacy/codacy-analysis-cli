package com.codacy.analysis.cli.clients

import java.nio.file.Path

import com.codacy.analysis.cli.clients.api.{ProjectConfiguration, RemoteResultResponse}
import com.codacy.api.dtos.{Language, Languages}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, ParsingFailure}
import org.log4s.{Logger, getLogger}
import scalaj.http.{Http, HttpResponse}

import scala.concurrent.Future
import com.codacy.analysis.cli.model.{Result, ToolResults}
import codacy.docker
import cats.implicits._
import scala.concurrent.ExecutionContext

class CodacyClient(apiUrl: Option[String] = None, extraHeaders: Map[String, String], credentials: Credentials)(
  implicit context: ExecutionContext) {

  private val logger: Logger = getLogger

  private val remoteUrl = apiUrl.getOrElse("https://api.codacy.com") + "/2.0"
  private lazy val connectionTimeoutMs = 2000
  private lazy val readTimeoutMs = 5000

  private implicit val levelEncoder: Encoder[docker.api.Result.Level.Value] =
    Encoder.enumEncoder(docker.api.Result.Level)
  private implicit val categoryEncoder: Encoder[docker.api.Pattern.Category.Value] =
    Encoder.enumEncoder(docker.api.Pattern.Category)
  private implicit val pathEncoder: Encoder[Path] = Encoder[String].contramap(_.toString)
  private implicit val languageDecoder: Decoder[Language] =
    Decoder[String].emap(lang =>
      Languages.fromName(lang).fold[Either[String, Language]](Left(s"Failed to parse language $lang"))(Right(_)))

  private def get(endpoint: String): Either[ParsingFailure, Json] = {
    val headers: Map[String, String] = Map("Content-Type" -> "application/json") ++ extraHeaders

    val response: HttpResponse[String] =
      Http(s"$remoteUrl$endpoint").headers(headers).timeout(connectionTimeoutMs, readTimeoutMs).asString

    parse(response.body)
  }

  private def post(endpoint: String, data: Json): Either[ParsingFailure, Json] = {
    val headers: Map[String, String] = Map("Content-Type" -> "application/json") ++ extraHeaders

    val response: HttpResponse[String] =
      Http(s"$remoteUrl$endpoint")
        .headers(headers)
        .timeout(connectionTimeoutMs, readTimeoutMs)
        .postData(data.toString)
        .asString

    parse(response.body)
  }

  def getRemoteConfiguration: Either[String, ProjectConfiguration] = {
    credentials match {
      case token: APIToken => getProjectConfiguration(token.userName, token.projectName)
      case _: ProjectToken => getProjectConfiguration
    }
  }

  def sendRemoteResults(tool: String, commitUuid: String, results: Seq[Result]): Future[Either[String, Unit]] = {
    credentials match {
      case token: APIToken =>
        sendRemoteResultsTo(s"/${token.userName}/${token.projectName}/commit/$commitUuid/remoteResults", tool, results)
      case _: ProjectToken => sendRemoteResultsTo(s"/commit/$commitUuid/remoteResults", tool, results)
    }
  }

  def sendEndOfResults(commitUuid: String): Future[Either[String, Unit]] = {
    credentials match {
      case token: APIToken =>
        sendEndOfResultsTo(s"/${token.userName}/${token.projectName}/commit/$commitUuid/endRemoteResults")
      case _: ProjectToken => sendEndOfResultsTo(s"/commit/$commitUuid/endRemoteResults")
    }
  }

  private def getProjectConfiguration: Either[String, ProjectConfiguration] = {
    getProjectConfigurationFrom("/project/analysis/configuration")
  }

  private def getProjectConfiguration(projectName: String, username: String): Either[String, ProjectConfiguration] = {
    getProjectConfigurationFrom(s"/project/$username/$projectName/analysis/configuration")
  }

  private def sendRemoteResultsTo(endpoint: String, tool: String, results: Seq[Result]): Future[Either[String, Unit]] =
    Future {
      post(endpoint, ToolResults(tool, results).asJson) match {
        case Left(e) =>
          logger.error(e)(s"Error posting data to endpoint $endpoint")
          Left(e.message)
        case Right(json) => parseRemoteResultsResponse(json)
      }
    }

  private def sendEndOfResultsTo(endpoint: String): Future[Either[String, Unit]] = Future {
    get(endpoint) match {
      case Left(e) =>
        logger.error(e)(s"Error sending end of upload results to endpoint $endpoint")
        Left(e.message)
      case _ => Right(())
    }
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

  private def parseRemoteResultsResponse(json: Json): Either[String, Unit] = {
    json.as[RemoteResultResponse] match {
      case Left(e) =>
        logger.error(e)("Error parsing remote results upload response")
        Left(e.message)
      case Right(_) => ().asRight[String]
    }
  }
}

object CodacyClient {

  def apply(credentials: Credentials)(implicit context: ExecutionContext): CodacyClient = {
    credentials match {
      case ProjectToken(token, baseUrl) =>
        val headers: Map[String, String] = Map(("project_token", token))
        new CodacyClient(baseUrl, headers, credentials)
      case APIToken(token, baseUrl, _, _) =>
        val headers: Map[String, String] = Map(("api_token", token))
        new CodacyClient(baseUrl, headers, credentials)
    }
  }

}
