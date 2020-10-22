package com.codacy.toolRepository.remote.storage

import com.codacy.analysis.core.model.ToolSpec
import com.codacy.analysis.core.storage.FileDataStorage
import io.circe.{Decoder, Encoder}

case class ToolSpecDataStorage(override val storageFilename: String = "tools")
    extends FileDataStorage[ToolSpec](storageFilename) {

  override implicit val encoder: Encoder[ToolSpec] =
    ToolPatternsSpecsEncoders.toolEncoder

  override implicit val decoder: Decoder[ToolSpec] =
    ToolPatternsSpecsEncoders.toolDecoder
}
