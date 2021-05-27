package com.codacy.analysis.core.tools

import com.codacy.plugins.api.languages.Language

trait ITool {
  def names: Seq[String]
  def supportedLanguages: Set[Language]
  def languageToRun: Language
}
