package com.codacy.analysis.core.model

import play.api.libs.json.JsValue

sealed trait Configuration {
  val baseSubDir: Option[String]
  val extraValues: Option[Map[String, JsValue]]
}

final case class Parameter(name: String, value: String)

final case class Pattern(id: String, parameters: Set[Parameter])

final case class CodacyCfg(patterns: Set[Pattern],
                           baseSubDir: Option[String] = Option.empty[String],
                           extraValues: Option[Map[String, JsValue]] = Option.empty[Map[String, JsValue]])
    extends Configuration

final case class FileCfg(baseSubDir: Option[String] = Option.empty[String],
                         extraValues: Option[Map[String, JsValue]] = Option.empty[Map[String, JsValue]])
    extends Configuration
