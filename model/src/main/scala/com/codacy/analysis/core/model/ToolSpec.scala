package com.codacy.analysis.core.model

import com.codacy.plugins.api.languages.Language

case class ToolSpec(uuid: String,
                    dockerImage: String,
                    isDefault: Boolean,
                    version: String,
                    languages: Set[Language],
                    name: String,
                    shortName: String,
                    documentationUrl: Option[String],
                    sourceCodeUrl: Option[String],
                    prefix: String,
                    needsCompilation: Boolean,
                    hasConfigFile: Boolean,
                    configFilenames: Set[String],
                    standalone: Boolean,
                    hasUIConfiguration: Boolean)
