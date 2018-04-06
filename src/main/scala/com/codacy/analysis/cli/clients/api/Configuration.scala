package com.codacy.analysis.cli.clients.api

final case class FilePath(value: String)
final case class LanguageExtensions(language: String, extensions: Set[String])
final case class ToolParameter(name: String, value: String)
final case class ToolPattern(internalId: String, parameters: Set[ToolParameter])
final case class ToolConfiguration(uuid: String,
                                   isEnabled: Boolean,
                                   notEdited: Boolean,
                                   patterns: Set[ToolPattern])
final case class ProjectConfiguration(ignoredPaths: Set[FilePath],
                                      projectExtensions: Set[LanguageExtensions],
                                      toolConfiguration: Set[ToolConfiguration])


