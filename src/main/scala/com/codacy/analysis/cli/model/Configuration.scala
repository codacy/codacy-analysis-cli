package com.codacy.analysis.cli.model

sealed trait Configuration

final case class Parameter(name: String, value: String)

final case class Pattern(id: String, parameters: Set[Parameter])

final case class CodacyCfg(patterns: Set[Pattern]) extends Configuration

case object FileCfg extends Configuration
