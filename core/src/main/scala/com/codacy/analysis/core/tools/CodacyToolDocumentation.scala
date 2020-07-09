package com.codacy.plugins.results.traits

import com.codacy.plugins.api.{PatternDescription, results}

class CodacyToolDocumentation(override val spec: Option[results.Tool.Specification],
                              override val descriptions: Option[Set[PatternDescription]],
                              override val toolDescription: Option[String])
    extends ToolDocumentation {
  override private[traits] val specPrivate: Option[results.Tool.Specification] = spec
  override private[traits] val descriptionsPrivate: Option[Set[PatternDescription]] = descriptions
}
