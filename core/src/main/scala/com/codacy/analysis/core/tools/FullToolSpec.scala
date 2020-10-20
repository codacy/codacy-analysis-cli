package com.codacy.analysis.core.tools

import com.codacy.analysis.core.model.{ParameterSpec, PatternSpec, ToolSpec}
import com.codacy.plugins.api

case class FullToolSpec(tool: ToolSpec, patterns: Seq[PatternSpec]) {

  val toolApiSpec: api.results.Tool.Specification = toToolApiSpecification(tool, patterns)
  val patternDescriptions: Set[api.PatternDescription] = toToolApiPatternDescriptions(patterns)

  private def toToolApiSpecification(toolSpec: ToolSpec, patterns: Seq[PatternSpec]): api.results.Tool.Specification = {
    api.results.Tool.Specification(
      name = api.results.Tool.Name(toolSpec.shortName),
      version = Some(api.results.Tool.Version(toolSpec.version)),
      patterns = toPatternApiSpecification(patterns))
  }

  private def toPatternApiSpecification(patterns: Seq[PatternSpec]): Set[api.results.Pattern.Specification] = {
    patterns.map { pattern =>
      api.results.Pattern.Specification(
        patternId = api.results.Pattern.Id(pattern.id),
        level = api.results.Result.Level.withName(pattern.level),
        category = api.results.Pattern.Category.withName(pattern.category),
        subcategory = pattern.subCategory.map(api.results.Pattern.Subcategory.withName),
        parameters = toParameterApiSpecification(pattern.parameters),
        languages = pattern.languages,
        enabled = pattern.enabled)
    }(collection.breakOut)
  }

  private def toParameterApiSpecification(parameters: Seq[ParameterSpec]): Set[api.results.Parameter.Specification] = {
    parameters.map { parameter =>
      api.results.Parameter.Specification(
        name = api.results.Parameter.Name(parameter.name),
        default = api.results.Parameter.Value(parameter.default))
    }(collection.breakOut)
  }

  private def toToolApiPatternDescriptions(patterns: Seq[PatternSpec]): Set[api.PatternDescription] = {
    patterns.map { pattern =>
      api.PatternDescription(
        patternId = api.results.Pattern.Id(pattern.id),
        title = pattern.title,
        parameters = Some(toToolApiParameterDescriptions(pattern.parameters)),
        description = pattern.description,
        timeToFix = pattern.timeToFix,
        explanation = pattern.explanation)
    }(collection.breakOut)
  }

  private def toToolApiParameterDescriptions(parameters: Seq[ParameterSpec]): Set[api.ParameterDescription] = {
    parameters.collect {
      case ParameterSpec(name, _, Some(description)) =>
        api.ParameterDescription(api.results.Parameter.Name(name), description)
    }(collection.breakOut)
  }
}
