package com.codacy.analysis.cli.clients

import com.codacy.analysis.cli.command.APIOptions
import com.codacy.analysis.cli.configuration.Environment
import com.codacy.analysis.cli.utils.Implicits._
import org.log4s.{Logger, getLogger}

sealed trait Credentials {
  val baseUrl: Option[String]
}

final case class ProjectToken(token: String, baseUrl: Option[String] = Option.empty[String]) extends Credentials
final case class APIToken(token: String,
                          baseUrl: Option[String] = Option.empty[String],
                          userName: String,
                          projectName: String)
    extends Credentials

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
          .flatMap {
            getCredentialsWithAdditionalParams(_, apiURL, options.project, options.username)
          }
          .ifEmpty(logger.info("Could not retrieve API token"))
      }
      .ifEmpty(logger.warn("Could not retrieve credentials"))
  }

  private def getCredentialsWithAdditionalParams(apiToken: String,
                                                 apiUrlOpt: Option[String],
                                                 projectOpt: Option[String],
                                                 userNameOpt: Option[String]): Option[Credentials] = {
    (for {
      project <- projectOpt
      userName <- userNameOpt
    } yield {
      APIToken(apiToken, apiUrlOpt, userName, project)
    }).ifEmpty(logger.warn("Could not username and/or project"))
  }
}
