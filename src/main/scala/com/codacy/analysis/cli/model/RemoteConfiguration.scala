package com.codacy.analysis.cli.model

import com.codacy.api.dtos.Language

final case class FilePath(value: String) extends AnyVal

final case class LanguageExtensions(language: Language, extensions: Set[String])

final case class RemoteConfiguration(ignoredPaths: Set[FilePath], projectExtensions: Set[LanguageExtensions])
