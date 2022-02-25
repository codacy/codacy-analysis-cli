package com.codacy.analysis.cli.analysis

import com.codacy.analysis.cli.CLIError
import com.codacy.analysis.cli.configuration.CLIConfiguration
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.tools._
import com.codacy.analysis.core.utils.InheritanceOps.InheritanceOps
import com.codacy.plugins.api.languages.Language

class ToolSelector(toolRepository: ToolRepository) {

  private val toolCollector = new ToolCollector(toolRepository)
  private val duplicationToolCollector = new DuplicationToolCollector(toolRepository)

  def allTools(toolInput: Option[String],
               configuration: CLIConfiguration.Tool,
               languages: Set[Language]): Either[CLIError, Set[ITool]] = {

    def toolsEither = tools(toolInput, configuration, languages)
    def metricsTools = MetricsToolCollector.fromLanguages(languages)
    def duplicationToolsEither =
      duplicationToolCollector.fromLanguages(languages).left.map(e => CLIError.CouldNotGetTools(e.message))

    toolInput match {
      case None =>
        for {
          tools <- toolsEither
          duplicationTools <- duplicationToolsEither
        } yield tools ++ metricsTools ++ duplicationTools

      case Some("metrics") =>
        Right(metricsTools.map(_.to[ITool]))

      case Some("duplication") =>
        duplicationToolsEither.map(_.map(_.to[ITool]))

      case Some("issues") =>
        val toolsEither = tools(None, configuration, languages)
        toolsEither.map(_.map(_.to[ITool]))

      case Some(_) =>
        val toolsEither = tools(toolInput, configuration, languages)
        toolsEither.map(_.map(_.to[ITool]))
    }
  }

  def tools(toolInput: Option[String],
            configuration: CLIConfiguration.Tool,
            languages: Set[Language]): Either[CLIError, Set[Tool]] = {

    def fromRemoteConfig: Either[CLIError, Set[Tool]] = {
      configuration.toolConfigurations.left.map(CLIError.NoRemoteProjectConfiguration).flatMap { toolConfiguration =>
        val toolUuids = toolConfiguration.filter(_.enabled).map(_.uuid)
        toolCollector
          .fromToolUUIDs(toolUuids, languages)
          .left
          .map(_ => CLIError.NonExistentToolsFromRemoteConfiguration(toolUuids))
      }
    }

    def fromLocalConfig: Either[CLIError, Set[Tool]] = {
      toolCollector.fromLanguages(languages).left.map(CLIError.from)
    }

    toolInput.map { toolStr =>
      toolCollector.fromNameOrUUID(toolStr, languages).left.map(CLIError.from)
    }.getOrElse {
      for {
        e1 <- fromRemoteConfig.left
        e2 <- fromLocalConfig.left
      } yield CLIError.CouldNotGetTools(s"${e1.message} and ${e2.message}")
    }
  }

  def fromUuid(uuid: String): Either[AnalyserError, FullToolSpec] = {
    toolCollector.fromUuid(uuid)
  }
}
