package com.codacy.analysis.core.clients

import java.nio.file.Path

import cats.implicits._
import com.codacy.analysis.core.clients.api.{CodacyError, ProjectConfiguration, RemoteResultResponse}
import com.codacy.analysis.core.model.IssuesAnalysis.FileResults
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.utils.HttpHelper
import com.codacy.plugins.api.languages.{Language, Languages}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import org.log4s.{Logger, getLogger}

import scala.concurrent.{ExecutionContext, Future}

class CodacyClient(credentials: Credentials, http: HttpHelper)(implicit context: ExecutionContext) {

  private val logger: Logger = getLogger

  private implicit val levelEncoder: Encoder[com.codacy.plugins.api.results.Result.Level.Value] =
    Encoder.enumEncoder(com.codacy.plugins.api.results.Result.Level)
  private implicit val categoryEncoder: Encoder[com.codacy.plugins.api.results.Pattern.Category.Value] =
    Encoder.enumEncoder(com.codacy.plugins.api.results.Pattern.Category)
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

  def sendRemoteIssues(tool: String,
                       commitUuid: String,
                       results: Either[String, Set[FileResults]]): Future[Either[String, Unit]] = {
    credentials match {
      case token: APIToken =>
        sendRemoteResultsTo(
          s"/${token.userName}/${token.projectName}/commit/$commitUuid/issuesRemoteResults",
          tool,
          results)
      case _: ProjectToken => sendRemoteResultsTo(s"/commit/$commitUuid/issuesRemoteResults", tool, results)
    }
  }

  def sendRemoteMetrics(commitUuid: String, results: Seq[MetricsResult]): Future[Either[String, Unit]] = {
    credentials match {
      case token: APIToken =>
        sendRemoteMetricsTo(s"/${token.userName}/${token.projectName}/commit/$commitUuid/metricsRemoteResults", results)
      case _: ProjectToken => sendRemoteMetricsTo(s"/commit/$commitUuid/metricsRemoteResults", results)
    }
  }

  def sendRemoteDuplication(commitUuid: String, results: Seq[DuplicationResult]): Future[Either[String, Unit]] = {
    credentials match {
      case token: APIToken =>
        sendRemoteDuplicationTo(
          s"/${token.userName}/${token.projectName}/commit/$commitUuid/duplicationRemoteResults",
          results)
      case _: ProjectToken =>
        sendRemoteDuplicationTo(s"/commit/$commitUuid/duplicationRemoteResults", results)
    }
  }

  def sendEndOfResults(commitUuid: String): Future[Either[String, Unit]] = {
    credentials match {
      case token: APIToken =>
        sendEndOfResultsTo(s"/${token.userName}/${token.projectName}/commit/$commitUuid/resultsFinal")
      case _: ProjectToken => sendEndOfResultsTo(s"/commit/$commitUuid/resultsFinal")
    }
  }

  private def getProjectConfiguration: Either[String, ProjectConfiguration] = {
    getProjectConfigurationFrom("/project/analysis/configuration")
  }

  private def getProjectConfiguration(username: UserName,
                                      projectName: ProjectName): Either[String, ProjectConfiguration] = {
    getProjectConfigurationFrom(s"/project/$username/$projectName/analysis/configuration")
  }

  private def sendRemoteMetricsTo(endpoint: String, metrics: Seq[MetricsResult]): Future[Either[String, Unit]] =
    Future {
      http.post(endpoint, Some(metrics.asJson)) match {
        case Left(error) =>
          logger.error(error)(s"Error posting data to endpoint $endpoint")
          Left(error.message)
        case Right(json) =>
          logger.info(s"""Success posting batch of ${metrics.size} files metrics to endpoint "$endpoint" """)
          validateRemoteResultsResponse(json)
      }
    }

  private def sendRemoteDuplicationTo(endpoint: String,
                                      duplication: Seq[DuplicationResult]): Future[Either[String, Unit]] =
    Future {
      http.post(endpoint, Some(duplication.asJson)) match {
        case Left(error) =>
          logger.error(error)(s"Error posting data to endpoint $endpoint")
          Left(error.message)
        case Right(json) =>
          logger.info(s"""Success posting batch of ${duplication.size} duplication clones to endpoint "$endpoint" """)
          validateRemoteResultsResponse(json)
      }
    }

  private def sendRemoteResultsTo(endpoint: String,
                                  tool: String,
                                  results: Either[String, Set[FileResults]]): Future[Either[String, Unit]] =
    Future {
      http.post(
        endpoint,
        Some(Seq(ToolResults(tool, results.fold(IssuesAnalysis.Failure, IssuesAnalysis.Success))).asJson)) match {
        case Left(error) =>
          logger.error(error)(s"Error posting data to endpoint $endpoint")
          Left(error.message)
        case Right(json) =>
          results.fold(
            error => logger.info(s"Success posting analysis error $error"),
            results =>
              logger.info(s"""Success posting batch of ${results.size} files with ${results
                .map(_.results.size)
                .sum} results to endpoint "$endpoint" """))

          validateRemoteResultsResponse(json)
      }
    }

  private def sendEndOfResultsTo(endpoint: String): Future[Either[String, Unit]] = Future {
    http.post(endpoint, None) match {
      case Left(error) =>
        logger.error(error)(s"Error sending end of upload results to endpoint $endpoint")
        Left(error.message)
      case Right(json) =>
        logger.info(s"""Success posting end of results to endpoint "$endpoint" """)
        validateRemoteResultsResponse(json, isEnd = true)
    }
  }

  private def getProjectConfigurationFrom(endpoint: String) = {
    http.get(endpoint) match {
      case Left(error) =>
        logger.error(error)(s"""Error getting config file from endpoint "$endpoint" """)
        Left(error.message)
      case Right(json) =>
        logger.info(s"""Success getting config file from endpoint "$endpoint" """)
        parseProjectConfigResponse(json)
    }
  }

  private def parseProjectConfigResponse(json: Json): Either[String, ProjectConfiguration] = {
    parse[ProjectConfiguration]("getting Project Configuration", json).map { p =>
      logger.info("Success parsing remote configuration")
      p
    }
  }

  private def validateRemoteResultsResponse(json: Json, isEnd: Boolean = false): Either[String, Unit] = {
    val action = if (isEnd) "end of results" else "sending results"
    val message = s"Endpoint for $action replied with an error"
    parse[RemoteResultResponse](message, json).map { _ =>
      logger.info("Success parsing remote results response ")
      ()
    }
  }

  private def parse[T](message: String, json: Json)(implicit decoder: Decoder[T]): Either[String, T] = {
    json.as[T].leftMap { error =>
      json.as[CodacyError] match {
        case Right(codacyError) =>
          val fullMessage = s"Error: $message : ${codacyError.error}"
          logger.error(fullMessage)
          fullMessage
        case _ =>
          logger.error(error)(s"Error parsing remote results upload response: $json")
          error.message
      }
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
