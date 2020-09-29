package com.codacy.analysis.core.model

case class PatternSpec(id: String,
                       level: String,
                       category: String,
                       subCategory: Option[String],
                       title: String,
                       shortDescription: Option[String],
                       description: Option[String],
                       enabledByDefault: Boolean,
                       timeToFix: Option[Int],
                       parameters: Seq[ParameterSpec])
