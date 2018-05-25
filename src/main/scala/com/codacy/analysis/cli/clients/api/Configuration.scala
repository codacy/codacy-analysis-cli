package com.codacy.analysis.cli.clients.api

import com.codacy.api.dtos.Language

final case class FilePath(value: String)
final case class LanguageExtensions(language: Language, extensions: Set[String])
final case class PathRegex(value: String)

final case class ToolParameter(name: String, value: String)
final case class ToolPattern(internalId: String, parameters: Set[ToolParameter])
final case class ToolConfiguration(uuid: String, isEnabled: Boolean, notEdited: Boolean, patterns: Set[ToolPattern])

final case class ProjectConfiguration(ignoredPaths: Set[FilePath],
                                      defaultIgnores: Set[PathRegex],
                                      projectExtensions: Set[LanguageExtensions],
                                      toolConfiguration: Set[ToolConfiguration])

final case class CodacyError(error: String)
