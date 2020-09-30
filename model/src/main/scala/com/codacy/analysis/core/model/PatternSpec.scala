package com.codacy.analysis.core.model

import com.codacy.plugins.api.languages.Language

//TODO: Revisit those types, to match our public API as much as possible?
case class PatternSpec(id: String,
                       level: String,
                       category: String,
                       subCategory: Option[String],
                       title: String,
                       description: Option[String],
                       explanation: Option[String],
                       enabled: Boolean,
                       timeToFix: Option[Int],
                       parameters: Seq[ParameterSpec],
                       languages: Set[Language])
