package com.codacy.toolRepository.plugins

import com.codacy.analysis.core.model.{AnalyserError, ParameterSpec, PatternSpec, ToolSpec}
import com.codacy.analysis.core.tools.ToolRepository
import com.codacy.plugins.api.PatternDescription
import com.codacy.plugins.api.results.Parameter
import com.codacy.plugins.results.traits.{DockerTool, DockerToolDocumentation}
import com.codacy.plugins.results.utils.ResultsTools
import com.codacy.plugins.utils.BinaryDockerHelper

class ToolRepositoryPlugins() extends ToolRepository {

  override lazy val list: Either[AnalyserError, Seq[ToolSpec]] = {
    val toolSpecs = ResultsTools.list.map { tool =>
      val dockerToolDocumentation = new DockerToolDocumentation(tool, new BinaryDockerHelper())
      val version = dockerToolDocumentation.toolSpecification.flatMap(_.version.map(_.value)).getOrElse("")
      ToolSpec(
        tool.uuid,
        tool.dockerImageName,
        tool.isDefault,
        version,
        tool.languages,
        tool.name,
        tool.shortName,
        tool.documentationUrl,
        tool.sourceCodeUrl,
        tool.prefix,
        tool.needsCompilation,
        hasConfigFile = false,
        tool.configFilename.to[Set],
        tool.isClientSide,
        tool.hasUIConfiguration)
    }

    Right(toolSpecs)
  }

  override def get(uuid: String): Either[AnalyserError, ToolSpec] =
    list.flatMap { toolsSpecs =>
      toolsSpecs.find(_.uuid == uuid) match {
        case None       => Left(AnalyserError.FailedToFindTool(uuid))
        case Some(tool) => Right(tool)
      }
    }

  override def listPatterns(toolUuid: String): Either[AnalyserError, Seq[PatternSpec]] = {
    val pluginEither =
      ResultsTools.list.find(_.uuid == toolUuid).toRight(AnalyserError.FailedToFindTool(toolUuid))
    for {
      plugin <- pluginEither
      dockerToolDocumentation = new DockerToolDocumentation(plugin, new BinaryDockerHelper())
      patternDescriptions <- dockerToolDocumentation.patternDescriptions.toRight(
        AnalyserError.FailedToListPatterns(toolUuid, "Cannot fetch patterns descriptions"))
      patternDescriptionsMap: Map[String, PatternDescription] =
        patternDescriptions.map(description => description.patternId.value -> description)(collection.breakOut)
      toolSpecification <- dockerToolDocumentation.prefixedSpecs.toRight(
        AnalyserError.FailedToListPatterns(toolUuid, "Cannot fetch tool specification"))
    } yield toolSpecification.patterns.map { pattern =>
      val patternDescription = patternDescriptionsMap.get(pattern.patternId.value)

      val parameters: Seq[ParameterSpec] = pattern.parameters.map {
        case Parameter.Specification(name, default) =>
          val parameterDescription =
            patternDescription.flatMap(_.parameters.getOrElse(Set.empty).find(_.name == name)).map(_.name.value)
          ParameterSpec(name.value, default.toString, parameterDescription)
      }(collection.breakOut)

      PatternSpec(
        pattern.patternId.value,
        pattern.level.toString,
        pattern.category.toString,
        pattern.subcategory.map(_.toString),
        patternDescription.map(_.title).getOrElse(pattern.patternId.value),
        patternDescription.flatMap(_.description),
        patternDescription.flatMap(_.explanation),
        pattern.enabled,
        timeToFix = patternDescription.flatMap(_.timeToFix),
        parameters = parameters,
        languages = pattern.languages)
    }(collection.breakOut)
  }
}
