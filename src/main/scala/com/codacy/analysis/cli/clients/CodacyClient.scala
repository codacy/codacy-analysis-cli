package com.codacy.analysis.cli.clients

import java.nio.file.Path

import com.codacy.analysis.cli.clients.api.{ProjectConfiguration, RemoteResultResponse}
import com.codacy.api.dtos.{Language, Languages}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import org.log4s.{Logger, getLogger}
import com.codacy.analysis.cli.model.{Result, ToolResults}
import codacy.docker
import cats.implicits._
import com.codacy.analysis.cli.utils.HttpHelper

import scala.concurrent.{ExecutionContext, Future}

class CodacyClient(credentials: Credentials, http: HttpHelper)(implicit context: ExecutionContext) {

  private val logger: Logger = getLogger

  private implicit val levelEncoder: Encoder[docker.api.Result.Level.Value] =
    Encoder.enumEncoder(docker.api.Result.Level)
  private implicit val categoryEncoder: Encoder[docker.api.Pattern.Category.Value] =
    Encoder.enumEncoder(docker.api.Pattern.Category)
  private implicit val pathEncoder: Encoder[Path] = Encoder[String].contramap(_.toString)
  private implicit val languageDecoder: Decoder[Language] =
    Decoder[String].emap(lang =>
      Languages.fromName(lang).fold[Either[String, Language]](Left(s"Failed to parse language $lang"))(Right(_)))

  def getRemoteConfiguration: Either[String, ProjectConfiguration] = {
    credentials match {
      case token: APIToken => getProjectConfiguration(token.userName, token.projectName)
      case _: ProjectToken => getProjectConfiguration
    }
  }

  def sendRemoteResults(tool: String, commitUuid: String, results: Set[Result]): Future[Either[String, Unit]] = {
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

  private def sendRemoteResultsTo(endpoint: String, tool: String, results: Set[Result]): Future[Either[String, Unit]] =
    Future {
      http.post(endpoint, Some(ToolResults(tool, results).asJson)) match {
        case Left(e) =>
          logger.error(e)(s"Error posting data to endpoint $endpoint")
          Left(e.message)
        case Right(json) =>
          logger.info(s"""Success posting batch of results to endpoint "$endpoint" """)
          validateRemoteResultsResponse(json)
      }
    }

  private def sendEndOfResultsTo(endpoint: String): Future[Either[String, Unit]] = Future {
    http.post(endpoint, None) match {
      case Left(e) =>
        logger.error(e)(s"Error sending end of upload results to endpoint $endpoint")
        Left(e.message)
      case Right(json) =>
        logger.info(s"""Success posting end of results to endpoint "$endpoint" """)
        validateRemoteResultsResponse(json)
    }
  }

  private def getProjectConfigurationFrom(endpoint: String) = {
    http.get(endpoint) match {
      case Left(e) =>
        logger.error(e)(s"""Error getting config file from endpoint "$endpoint" """)
        Left(e.message)
      case Right(json) =>
        logger.info(s"""Success getting config file from endpoint "$endpoint" """)
        parseProjectConfigResponse(json)
    }
  }

  private def parseProjectConfigResponse(json: Json): Either[String, ProjectConfiguration] =
    json.as[ProjectConfiguration] match {
      case Left(e) =>
        logger.error(e)("Error parsing config file")
        Left(e.message)
      case Right(p) =>
        logger.info("Success parsing remote configuration")
        Right(p)
    }

  private def validateRemoteResultsResponse(json: Json): Either[String, Unit] = {
    json.as[RemoteResultResponse] match {
      case Left(e) =>
        logger.error(e)("Error parsing remote results upload response")
        Left(e.message)
      case Right(_) =>
        logger.info("Success parsing remote results response ")
        ().asRight[String]
    }
  }
}

object CodacyClient {

  def apply(credentials: Credentials)(implicit context: ExecutionContext): CodacyClient = {
    credentials match {
      case ProjectToken(token, baseUrl) =>
        val headers: Map[String, String] = Map(("project_token", token))
        new CodacyClient(credentials, new HttpHelper(baseUrl, headers))
      case APIToken(token, baseUrl, _, _) =>
        val headers: Map[String, String] = Map(("api_token", token))
        new CodacyClient(credentials, new HttpHelper(baseUrl, headers))
    }
  }

}
