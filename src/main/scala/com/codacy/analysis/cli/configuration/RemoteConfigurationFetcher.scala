package com.codacy.analysis.cli.configuration

import com.codacy.analysis.cli.clients.{APIToken, CodacyClient, Credentials, ProjectToken}
import com.codacy.analysis.cli.clients.api.ProjectConfiguration
import com.codacy.analysis.cli.command.Analyse

class RemoteConfigurationFetcher(credentials: Credentials, codacyClient: CodacyClient, analyse: Analyse) {

  def getRemoteConfiguration: Either[String, ProjectConfiguration] = {
    credentials match {
      case _: APIToken =>
        for {
          userName <- analyse.api.username.toRight("Username not found")
          projectName <- analyse.api.project.toRight("Project name not found")
          config <- codacyClient.getProjectConfiguration(userName, projectName)
        } yield config

      case _: ProjectToken => codacyClient.getProjectConfiguration
    }
  }
}
