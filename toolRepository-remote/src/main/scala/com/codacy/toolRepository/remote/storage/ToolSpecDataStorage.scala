package com.codacy.toolRepository.remote.storage

import com.codacy.analysis.core.model.ToolSpec
import com.codacy.analysis.core.storage.FileDataStorage
import io.circe.{Decoder, Encoder}

trait ToolSpecDataStorageTrait extends FileDataStorage[ToolSpec] {

  override implicit val encoder: Encoder[ToolSpec] =
    ToolPatternsSpecsEncoders.toolEncoder

  override implicit val decoder: Decoder[ToolSpec] =
    ToolPatternsSpecsEncoders.toolDecoder

}

class ToolSpecDataStorage(override val storageFilename: String = "tools") extends ToolSpecDataStorageTrait

object ToolSpecDataStorage {

  def apply(storageFilename: String): ToolSpecDataStorage = {
    new ToolSpecDataStorage(storageFilename)
  }
}
