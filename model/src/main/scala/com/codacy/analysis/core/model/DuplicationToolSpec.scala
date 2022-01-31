package com.codacy.analysis.core.model

import com.codacy.plugins.api.languages.Language

case class DuplicationToolSpec(dockerImage: String, languages: Set[Language])
