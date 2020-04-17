package com.codacy.analysis.cli.configuration

import java.net.URL

import better.files.File
import com.codacy.analysis.core.utils.Implicits._
import org.log4s.{Logger, getLogger}

import scala.util.{Failure, Try}

class Environment(variables: Map[String, String]) {

  private val logger: Logger = getLogger

  def codeDirectoryEnvironmentVariable(): Option[String] = {
    validate("Project directory", "environment variable", "CODACY_CODE")(variables.get("CODACY_CODE"))
  }

  def projectTokenArgument(projectTokenFromArguments: Option[String]): Option[String] = {
    validate("Project token", "argument", "--project-token")(projectTokenFromArguments)
  }

  def projectTokenEnvironmentVariable(): Option[String] = {
    validate("Project token", "environment variable", "CODACY_PROJECT_TOKEN")(variables.get("CODACY_PROJECT_TOKEN"))
  }

  def apiTokenArgument(apiTokenFromArguments: Option[String]): Option[String] = {
    validate("API token", "argument", "--api-token")(apiTokenFromArguments)
  }

  def apiTokenEnvironmentVariable(): Option[String] = {
    validate("API token", "environment variable", "CODACY_API_TOKEN")(variables.get("CODACY_API_TOKEN"))
  }

  def apiBaseUrlArgument(codacyApiBaseURLFromArguments: Option[String]): Option[String] = {
    val apiURL =
      validate("API base URL", "argument", "--codacy-api-base-url")(codacyApiBaseURLFromArguments)
    validateApiBaseUrl(apiURL)
  }

  def apiBaseUrlEnvironmentVariable(): Option[String] = {
    val apiURL =
      validate("API base URL", "environment variable", "CODACY_API_BASE_URL")(variables.get("CODACY_API_BASE_URL"))
    validateApiBaseUrl(apiURL)
  }

  def baseProjectDirectory(directory: Option[File]): File =
    directory.fold(codeDirectoryEnvironmentVariable().map(File(_)).getOrElse(File.currentWorkingDirectory))(dir =>
      if (dir.isDirectory) dir else dir.parent)

  private def validateApiBaseUrl(apiURL: Option[String]): Option[String] = {
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

  private def validate(name: String, paramType: String, param: String)(value: Option[String]): Option[String] = {
    value.ifEmpty(logger.info(s"$name not passed through $paramType `$param`")).flatMap {
      case t if t.trim.nonEmpty =>
        logger.info(s"$name found in $paramType `$param`")
        Option(t.trim)
      case _ =>
        logger.warn(s"$name passed through $paramType `$param` is empty")
        Option.empty[String]
    }
  }
}
