package com.codacy.analysis.cli.clients

import com.codacy.analysis.cli.command.APIOptions
import com.codacy.analysis.cli.configuration.Environment
import com.codacy.analysis.core.clients._
import com.codacy.analysis.core.utils.Implicits._
import org.log4s.{Logger, getLogger}

object Credentials {

  private val logger: Logger = getLogger

  def get(environment: Environment, options: APIOptions, apiURL: String): Option[Credentials] = {
    environment
      .projectTokenArgument(options.projectToken)
      .map[Credentials](ProjectToken(_, apiURL))
      .orElse[Credentials] {
        environment
          .apiTokenArgument(options.apiToken)
          .flatMap(getCredentialsWithAdditionalParams(_, apiURL, options.provider, options.username, options.project))
      }
      .orElse[Credentials] {
        environment.projectTokenEnvironmentVariable().map[Credentials](ProjectToken(_, apiURL))
      }
      .orElse[Credentials] {
        environment
          .apiTokenEnvironmentVariable()
          .flatMap(getCredentialsWithAdditionalParams(_, apiURL, options.provider, options.username, options.project))
      }
      .ifEmpty(logger.warn("Could not retrieve credentials"))
  }

  private def getCredentialsWithAdditionalParams(apiToken: String,
                                                 apiUrl: String,
                                                 providerOpt: Option[OrganizationProvider.Value],
                                                 userNameOpt: Option[UserName],
                                                 projectOpt: Option[ProjectName]): Option[Credentials] = {
    (for {
      provider <- providerOpt
      userName <- userNameOpt
      project <- projectOpt
    } yield {
      APIToken(apiToken, apiUrl, provider, userName, project)
    }).ifEmpty(logger.warn("Could not retrieve provider, username and/or project"))
  }
}
