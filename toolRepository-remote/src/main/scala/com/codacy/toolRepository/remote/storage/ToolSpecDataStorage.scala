package com.codacy.toolRepository.remote.storage

import com.codacy.analysis.core.model.ToolSpec
import com.codacy.analysis.core.storage.FileDataStorage
import io.circe.{Decoder, Encoder}
import better.files.File

case class ToolSpecDataStorage(override val currentWorkingDirectory: File,
                               override val storageFilename: String = "tools")
    extends FileDataStorage[ToolSpec](currentWorkingDirectory, storageFilename) {

  override implicit val encoder: Encoder[ToolSpec] =
    ToolPatternsSpecsEncoders.toolEncoder

  override implicit val decoder: Decoder[ToolSpec] =
    ToolPatternsSpecsEncoders.toolDecoder
}
