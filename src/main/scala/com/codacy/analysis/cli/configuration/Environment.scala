package com.codacy.analysis.cli.configuration

import java.net.URL

import com.codacy.analysis.cli.utils.Implicits._
import org.log4s.{Logger, getLogger}

import scala.util.{Failure, Try}

class Environment(variables: Map[String, String]) {

  private val logger: Logger = getLogger

  def projectToken(projectToken: Option[String]): Option[String] = {
    projectToken
      .ifEmpty(logger.info(s"Project token not passed through argument `--project-token`"))
      .flatMap {
        case t if t.trim.nonEmpty => Option(t.trim)
        case _ =>
          logger.warn(s"Project token passed through argument `--project-token` is empty")
          Option.empty[String]
      }
      .orElse(variables.get("CODACY_PROJECT_TOKEN"))
      .ifEmpty(logger.info(s"Project token not available in the environment variable `CODACY_PROJECT_TOKEN`"))
      .flatMap {
        case t if t.trim.nonEmpty => Option(t.trim)
        case _ =>
          logger.warn(s"Project token passed through argument `CODACY_PROJECT_TOKEN` is empty")
          Option.empty[String]
      }
  }

  def apiToken(apiToken: Option[String]): Option[String] = {
    apiToken
      .ifEmpty(logger.info(s"API token not passed through argument `--api-token`"))
      .flatMap {
        case t if t.trim.nonEmpty => Option(t.trim)
        case _ =>
          logger.warn(s"API token passed through argument `--api-token` is empty")
          Option.empty[String]
      }
      .orElse(variables.get("CODACY_API_TOKEN"))
      .ifEmpty(logger.info(s"API token not available in the environment variable `CODACY_API_TOKEN`"))
      .flatMap {
        case t if t.trim.nonEmpty => Option(t.trim)
        case _ =>
          logger.warn(s"API token passed through argument `CODACY_API_TOKEN` is empty")
          Option.empty[String]
      }
  }

  def apiBaseUrl(codacyApiBaseURL: Option[String]): Option[String] = {
    val apiURL =
      codacyApiBaseURL
        .ifEmpty(logger.info(s"API base URL not passed through argument `--codacy-api-base-url`"))
        .flatMap {
          case t if t.trim.nonEmpty => Option(t.trim)
          case _ =>
            logger.warn(s"API base URL passed through argument `--codacy-api-base-url` is empty")
            Option.empty[String]
        }
        .orElse(variables.get("CODACY_API_BASE_URL"))
        .ifEmpty(logger.info(s"API base URL not available in the environment variable `CODACY_API_BASE_URL`"))
        .flatMap {
          case t if t.trim.nonEmpty => Option(t.trim)
          case _ =>
            logger.warn(s"API base URL passed through argument `CODACY_API_BASE_URL` is empty")
            Option.empty[String]
        }

    apiURL.flatMap { url =>
      Try(new URL(url)) match {
        case Failure(_) =>
          val error = s"Invalid API base URL: $url"

          val help = if (!url.startsWith("http")) {
            " * Maybe you forgot the http:// or https:// ?"
          }

          logger.warn(s"$error\n$help")
          Option.empty[String]

        case _ =>
          logger.info(s"Using API base URL $url")
          Option(url)
      }
    }
  }

}
