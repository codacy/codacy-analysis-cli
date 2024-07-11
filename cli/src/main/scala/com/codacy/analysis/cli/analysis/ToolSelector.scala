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
  private val metricsToolCollector = new MetricsToolCollector(toolRepository)

  def allTools(toolInputOpt: Option[String],
               configuration: CLIConfiguration.Tool,
               languages: Set[Language],
               registryAddress: String): Either[CLIError, Set[ITool]] = {

    def duplicationToolsEither: Either[CLIError.CouldNotGetTools, Set[DuplicationTool]] =
      duplicationToolCollector
        .fromLanguages(languages, registryAddress)
        .left
        .map(e => CLIError.CouldNotGetTools(e.message))

    def metricsToolsEither: Either[CLIError.CouldNotGetTools, Set[MetricsTool]] =
      metricsToolCollector.fromLanguages(languages, registryAddress).left.map(e => CLIError.CouldNotGetTools(e.message))

    toolInputOpt match {
      case None =>
        for {
          tools <- tools(configuration, languages, registryAddress)
          duplicationTools <- duplicationToolsEither
          metricsTools <- metricsToolsEither
        } yield tools ++ metricsTools ++ duplicationTools

      case Some("metrics") =>
        metricsToolsEither.map(_.map(_.to[ITool]))

      case Some("duplication") =>
        duplicationToolsEither.map(_.map(_.to[ITool]))

      case Some("issues") =>
        val toolsEither = tools(configuration, languages, registryAddress)
        toolsEither.map(_.map(_.to[ITool]))

      case Some(toolInput) =>
        val toolEither = tool(toolInput, languages, registryAddress)
        toolEither.map(_.map(_.to[ITool]))
    }
  }

  def tools(configuration: CLIConfiguration.Tool,
            languages: Set[Language],
            registryAddress: String): Either[CLIError, Set[Tool]] = {
    def fromRemoteConfig: Either[CLIError, Set[Tool]] = {
      configuration.toolConfigurations.left.map(CLIError.NoRemoteProjectConfiguration).flatMap { toolConfiguration =>
        val toolUuids = toolConfiguration.filter(_.enabled).map(_.uuid)
        toolCollector
          .fromToolUUIDs(toolUuids, languages, registryAddress)
          .left
          .map(_ => CLIError.NonExistentToolsFromRemoteConfiguration(toolUuids))
      }
    }

    def fromLocalConfig: Either[CLIError, Set[Tool]] = {
      toolCollector.fromLanguages(languages, registryAddress).left.map(CLIError.from)
    }

    for {
      e1 <- fromRemoteConfig.left
      e2 <- fromLocalConfig.left
    } yield CLIError.CouldNotGetTools(s"${e1.message} and ${e2.message}")
  }

  def tool(toolInput: String, languages: Set[Language], registryAddress: String): Either[CLIError, Set[Tool]] = {
    toolCollector.fromNameOrUUID(toolInput, languages, registryAddress).left.map(CLIError.from)
  }

  def fromUuid(uuid: String): Either[AnalyserError, FullToolSpec] = {
    toolCollector.fromUuid(uuid)
  }
}
