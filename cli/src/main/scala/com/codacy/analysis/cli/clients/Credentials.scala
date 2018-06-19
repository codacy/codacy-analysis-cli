package com.codacy.analysis.cli.clients

import com.codacy.analysis.cli.command.APIOptions
import com.codacy.analysis.cli.configuration.Environment
import com.codacy.analysis.core.clients._
import com.codacy.analysis.core.utils.Implicits._
import org.log4s.{Logger, getLogger}

object Credentials {

  private val logger: Logger = getLogger

  def get(environment: Environment, options: APIOptions): Option[Credentials] = {
    val apiURL =
      environment.apiBaseUrlArgument(options.codacyApiBaseUrl).orElse(environment.apiBaseUrlEnvironmentVariable())

    environment
      .projectTokenArgument(options.projectToken)
      .map[Credentials](ProjectToken(_, apiURL))
      .orElse[Credentials] {
      environment
        .apiTokenArgument(options.apiToken)
        .flatMap(getCredentialsWithAdditionalParams(_, apiURL, options.project, options.username))
    }
      .orElse[Credentials] {
      environment.projectTokenEnvironmentVariable().map[Credentials](ProjectToken(_, apiURL))
    }
      .orElse[Credentials] {
      environment
        .apiTokenEnvironmentVariable()
        .flatMap(getCredentialsWithAdditionalParams(_, apiURL, options.project, options.username))
    }
      .ifEmpty(logger.warn("Could not retrieve credentials"))
  }

  private def getCredentialsWithAdditionalParams(apiToken: String,
                                                 apiUrlOpt: Option[String],
                                                 projectOpt: Option[ProjectName],
                                                 userNameOpt: Option[UserName]): Option[Credentials] = {
    (for {
      project <- projectOpt
      userName <- userNameOpt
    } yield {
      APIToken(apiToken, apiUrlOpt, userName, project)
    }).ifEmpty(logger.warn("Could not username and/or project"))
  }
}
