package com.codacy.analysis.core.model

import com.codacy.plugins.api.languages.Language

case class ToolSpec(uuid: String,
                    dockerImage: String,
                    isDefault: Boolean,
                    version: String,
                    languages: Set[Language],
                    name: String,
                    shortName: String,
                    documentationUrl: String,
                    sourceCodeUrl: String,
                    prefix: String,
                    needsCompilation: Boolean,
                    //TODO: Do we have this on the API, or we computed based on the filenames or something?
                    hasConfigFile: Boolean,
                    configFilenames: Set[String],
                    isClientSide: Boolean,
                    hasUIConfiguration: Boolean)
