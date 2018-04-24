package com.codacy.analysis.cli.clients

import com.codacy.analysis.cli.command.APIOptions
import com.codacy.analysis.cli.configuration.Environment
import com.codacy.analysis.cli.utils.Implicits._
import org.log4s.{Logger, getLogger}

sealed trait Credentials {
  val baseUrl: Option[String]
}

final case class ProjectToken(token: String, baseUrl: Option[String] = Option.empty[String]) extends Credentials
final case class APIToken(token: String, baseUrl: Option[String] = Option.empty[String]) extends Credentials

object Credentials {

  private val logger: Logger = getLogger

  def get(environment: Environment, options: APIOptions): Option[Credentials] = {
    val apiURL = environment.apiBaseUrl(options.codacyApiBaseUrl)

    environment
      .projectToken(options.projectToken)
      .ifEmpty(logger.info("Could not retrieve Project token"))
      .map[Credentials](ProjectToken(_, apiURL))
      .orElse[Credentials] {
        environment
          .apiToken(options.apiToken)
          .ifEmpty(logger.info("Could not retrieve API token"))
          .map[Credentials](apiToken => APIToken(apiToken, apiURL))
      }
      .ifEmpty(logger.warn("Could not retrieve credentials"))
  }
}
