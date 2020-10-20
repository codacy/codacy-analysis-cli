package com.codacy.toolRepository.remote

import com.codacy.analysis.core.model.{ParameterSpec, PatternSpec, ToolSpec}
import com.codacy.analysis.core.storage.FileDataStorage
import com.codacy.plugins.api.languages.{Language, Languages}
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

trait RemoteToolsDataStorageTrait extends FileDataStorage[RemoteToolInformation] {

  override implicit val encoder: Encoder[RemoteToolInformation] =
    RemoteToolInformationEncoders.toolInformationEncoder

  override implicit val decoder: Decoder[RemoteToolInformation] =
    RemoteToolInformationEncoders.toolInformationDecoder

  override def compare(current: RemoteToolInformation, value: RemoteToolInformation): Boolean = {
    current.toolSpec.uuid == value.toolSpec.uuid
  }

  override def storageFilename: String = "tools"

  def storeTools(tools: Seq[ToolSpec]): Boolean

  def storePatterns(toolUuid: String, patterns: Seq[PatternSpec]): Boolean

  def getTools(): Option[Seq[ToolSpec]]

  def getPatterns(toolUuid: String): Option[Seq[PatternSpec]]
}

class RemoteToolsDataStorage extends RemoteToolsDataStorageTrait {

  def mergeToolsList(currentList: Seq[RemoteToolInformation],
                     newListOfTools: Seq[ToolSpec]): Seq[RemoteToolInformation] = {
    newListOfTools.map { tool =>
      val toolInfoOpt = currentList.find(toolInfo =>
        toolInfo.toolSpec.uuid == tool.uuid && toolInfo.toolSpec.version == tool.version
      )

      toolInfoOpt match {
        case None    => RemoteToolInformation(tool, None)
        case Some(t) => t
      }
    }
  }

  def updatePatternsForTool(listOfTools: Seq[RemoteToolInformation],
                            toolUuid: String,
                            patterns: Seq[PatternSpec]): Seq[RemoteToolInformation] = {
    listOfTools.map { tool =>
      if (tool.toolSpec.uuid == toolUuid) {
        RemoteToolInformation(tool.toolSpec, Some(patterns))
      } else {
        tool
      }
    }
  }

  def storeTools(tools: Seq[ToolSpec]): Boolean = {
    val currentListOfTools = this.get().getOrElse(Seq.empty)
    val remoteToolsInfo = this.mergeToolsList(currentListOfTools, tools)

    this.put(remoteToolsInfo)
  }

  def storePatterns(toolUuid: String, patterns: Seq[PatternSpec]): Boolean = {
    val listOfTools = this.get().getOrElse(Seq.empty)
    val remoteToolsInfo = updatePatternsForTool(listOfTools, toolUuid, patterns)

    this.put(remoteToolsInfo)
  }

  def getTools(): Option[Seq[ToolSpec]] = {
    this.get().map(_.map(remoteToolInformation => remoteToolInformation.toolSpec))
  }

  def getPatterns(toolUuid: String): Option[Seq[PatternSpec]] = {
    this
      .get()
      .flatMap(_.find(_.toolSpec.uuid == toolUuid).flatMap(remoteToolInformation => remoteToolInformation.patterns))
  }
}

case class RemoteToolInformation(toolSpec: ToolSpec, patterns: Option[Seq[PatternSpec]])

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
