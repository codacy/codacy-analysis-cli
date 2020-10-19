package com.codacy.toolRepository.remote

import com.codacy.analysis.core.model.{AnalyserError, ParameterSpec, PatternSpec, ToolSpec}
import com.codacy.analysis.core.storage.FileDataStorage
import com.codacy.plugins.api.languages.{Language, Languages}
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

class RemoteToolsDataStorage extends FileDataStorage[RemoteToolInformation] {

  override implicit val encoder: Encoder[RemoteToolInformation] =
    RemoteToolInformationEncoders.toolInformationEncoder

  override implicit val decoder: Decoder[RemoteToolInformation] =
    RemoteToolInformationEncoders.toolInformationDecoder

  override def compare(current: RemoteToolInformation, value: RemoteToolInformation): Boolean = {
    current.toolSpec.uuid == value.toolSpec.uuid
  }

  override def storageFilename: String = "tools"

  def storeTools(tools: Seq[ToolSpec]): Unit = {
    val remoteToolsInfo = tools.map(RemoteToolInformation(_, Seq.empty))
    this.put(remoteToolsInfo)
  }

  def storePatterns(toolUuid: String, patterns: Seq[PatternSpec]): Unit = {
    val listOfTools = this.get().getOrElse(Seq.empty)
    val remoteToolsInfo = listOfTools.map { tool =>
      if (tool.toolSpec.uuid == toolUuid) {
        RemoteToolInformation(tool.toolSpec, patterns)
      } else {
        tool
      }
    }

    this.put(remoteToolsInfo)
  }

  def getToolsOrError(error: AnalyserError): Either[AnalyserError, Seq[ToolSpec]] = {
    this.get() match {
      case Some(storageInfo) => Right(storageInfo.map(remoteToolInformation => remoteToolInformation.toolSpec))
      case None              => Left(error)
    }
  }

  def getPatternsOrError(error: AnalyserError): Either[AnalyserError, Seq[PatternSpec]] = {
    this.get() match {
      case Some(storageInfo) => Right(storageInfo.map(remoteToolInformation => remoteToolInformation.patterns))
      case None              => Left(error)
    }
  }
}

case class RemoteToolInformation(toolSpec: ToolSpec, patterns: Seq[PatternSpec])

object RemoteToolInformationEncoders {

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
      isClientSide <- c.downField("isClientSide").as[Boolean]
      hasUIConfiguration <- c.downField("hasUIConfiguration").as[Boolean]
    } yield ToolSpec(
      uuid = uuid,
      dockerImage,
      isDefault,
      version,
      languages.filter(_ != null),
      name,
      shortName,
      documentationUrl,
      sourceCodeUrl,
      prefix,
      needsCompilation,
      hasConfigFile,
      configFilenames,
      isClientSide,
      hasUIConfiguration)

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

  implicit val toolInformationDecoder: Decoder[RemoteToolInformation] = deriveDecoder[RemoteToolInformation]
  implicit val toolInformationEncoder: Encoder[RemoteToolInformation] = deriveEncoder[RemoteToolInformation]
}
