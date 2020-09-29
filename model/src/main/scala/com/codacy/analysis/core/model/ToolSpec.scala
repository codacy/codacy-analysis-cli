package com.codacy.analysis.core.model

import com.codacy.plugins.api.languages.Language

case class ToolSpec(uuid: String,
                    dockerImage: String,
                    isDefault: Boolean,
                    languages: Set[Language],
                    name: String,
                    shortName: String,
                    documentationUrl: String,
                    sourceCodeUrl: String,
                    prefix: String,
                    needsCompilation: Boolean,
                    configFilename: Seq[String],
                    isClientSide: Boolean,
                    hasUIConfiguration: Boolean)
