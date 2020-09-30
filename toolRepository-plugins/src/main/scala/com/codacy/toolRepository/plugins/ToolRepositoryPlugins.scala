package com.codacy.toolRepository.plugins

import com.codacy.analysis.core.model.{ParameterSpec, PatternSpec, ToolSpec}
import com.codacy.analysis.core.tools.ToolRepository
import com.codacy.plugins.api.PatternDescription
import com.codacy.plugins.api.results.Parameter
import com.codacy.plugins.results.traits.DockerToolDocumentation
import com.codacy.plugins.results.utils.ResultsTools
import com.codacy.plugins.utils.BinaryDockerHelper

class ToolRepositoryPlugins() extends ToolRepository {

  override def list(): Seq[ToolSpec] = {
    ResultsTools.list.map { tool =>
      ToolSpec(
        tool.uuid,
        tool.dockerImageName,
        tool.isDefault,
        tool.languages,
        tool.name,
        tool.shortName,
        tool.documentationUrl,
        tool.sourceCodeUrl,
        tool.prefix,
        tool.needsCompilation,
        tool.configFilename,
        tool.isClientSide,
        tool.hasUIConfiguration)
    }
  }

  override def listPatterns(toolUuid: String): Seq[PatternSpec] = {
    val plugin = ResultsTools.list
      .find(_.uuid == toolUuid)
      .getOrElse(
        throw new Exception(s"Failed to list patterns for tool with UUID $toolUuid because tool was not found"))

    val dockerToolDocumentation = new DockerToolDocumentation(plugin, new BinaryDockerHelper())

    val patternSpecOpt: Option[Seq[PatternSpec]] = for {
      patternDescriptions <- dockerToolDocumentation.patternDescriptions
      patternDescriptionsMap: Map[String, PatternDescription] =
        patternDescriptions.map(description => description.patternId.value -> description)(collection.breakOut)
      toolSpecification <- dockerToolDocumentation.prefixedSpecs
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
        timeToFix = None,
        parameters = parameters)
    }(collection.breakOut)

    patternSpecOpt.getOrElse(throw new Exception(s"Failed to list patterns for tool with UUID $toolUuid"))
  }
}
