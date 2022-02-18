package com.codacy.analysis.core.model

import com.codacy.plugins.api.languages.Language

case class MetricsToolSpec(dockerImage: String, languages: Set[Language])
