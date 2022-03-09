package com.codacy.toolRepository.remote.storage

import com.codacy.analysis.core.model.{DuplicationToolSpec, MetricsToolSpec, ParameterSpec, PatternSpec, ToolSpec}
import com.codacy.plugins.api.languages.{Language, Languages}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, HCursor, Json}

object ToolPatternsSpecsEncoders {

  implicit val encodeLanguage: Encoder[Language] = (l: Language) => Json.obj(("name", Json.fromString(l.name)))

  implicit val decodeLanguage: Decoder[Language] = (c: HCursor) =>
    for {
      name <- c.downField("name").as[String]
    } yield Languages.fromName(name).orNull

  implicit val toolEncoder: Encoder[ToolSpec] = deriveEncoder[ToolSpec]

  implicit val toolDecoder: Decoder[ToolSpec] = (c: HCursor) =>
    for {
      uuid <- c.downField("uuid").as[String]
      dockerImage <- c.downField("dockerImage").as[String]
      isDefault <- c.downField("isDefault").as[Boolean]
      version <- c.downField("version").as[String]
      languages <- c.downField("languages").as[Set[Language]]
      name <- c.downField("name").as[String]
      shortName <- c.downField("shortName").as[String]
      documentationUrl <- c.downField("documentationUrl").as[Option[String]]
      sourceCodeUrl <- c.downField("sourceCodeUrl").as[Option[String]]
      prefix <- c.downField("prefix").as[String]
      needsCompilation <- c.downField("needsCompilation").as[Boolean]
      hasConfigFile <- c.downField("hasConfigFile").as[Boolean]
      configFilenames <- c.downField("configFilenames").as[Set[String]]
      standalone <- c.downField("standalone").as[Boolean]
      hasUIConfiguration <- c.downField("hasUIConfiguration").as[Boolean]
    } yield ToolSpec(
      uuid = uuid,
      dockerImage = dockerImage,
      isDefault = isDefault,
      version = version,
      languages = languages.filter(_ != null),
      name = name,
      shortName = shortName,
      documentationUrl = documentationUrl,
      sourceCodeUrl = sourceCodeUrl,
      prefix = prefix,
      needsCompilation = needsCompilation,
      hasConfigFile = hasConfigFile,
      configFilenames = configFilenames,
      standalone = standalone,
      hasUIConfiguration = hasUIConfiguration)

  implicit val duplicationToolEncoder: Encoder[DuplicationToolSpec] = deriveEncoder[DuplicationToolSpec]

  implicit val duplicationToolDecoder: Decoder[DuplicationToolSpec] = (c: HCursor) =>
    for {
      dockerImage <- c.downField("dockerImage").as[String]
      languages <- c.downField("languages").as[Set[Language]]
    } yield DuplicationToolSpec(dockerImage, languages.filter(_ != null))

  implicit val metricsToolEncoder: Encoder[MetricsToolSpec] = deriveEncoder[MetricsToolSpec]

  implicit val metricsToolDecoder: Decoder[MetricsToolSpec] = (c: HCursor) =>
    for {
      dockerImage <- c.downField("dockerImage").as[String]
      languages <- c.downField("languages").as[Set[Language]]
    } yield MetricsToolSpec(dockerImage, languages.filter(_ != null))

  implicit val patternParameterDecoder: Decoder[ParameterSpec] = deriveDecoder[ParameterSpec]
  implicit val patternParameterEncoder: Encoder[ParameterSpec] = deriveEncoder[ParameterSpec]

  implicit val toolPatternEncoder: Encoder[PatternSpec] = deriveEncoder[PatternSpec]

  implicit val toolPatternDecoder: Decoder[PatternSpec] = (c: HCursor) =>
    for {
      id <- c.downField("id").as[String]
      level <- c.downField("level").as[String]
      category <- c.downField("category").as[String]
      subCategory <- c.downField("subCategory").as[Option[String]]
      title <- c.downField("title").as[String]

      description <- c.downField("description").as[Option[String]]
      explanation <- c.downField("explanation").as[Option[String]]
      enabled <- c.downField("enabled").as[Boolean]
      timeToFix <- c.downField("timeToFix").as[Option[Int]]

      parameters <- c.downField("parameters").as[Seq[ParameterSpec]]
      languages <- c.downField("languages").as[Set[Language]]
    } yield PatternSpec(
      id,
      level,
      category,
      subCategory,
      title,
      description,
      explanation,
      enabled,
      timeToFix,
      parameters,
      languages.filter(_ != null))
}
