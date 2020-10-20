package com.codacy.toolRepository.remote.storage

import com.codacy.analysis.core.model.PatternSpec
import com.codacy.analysis.core.storage.FileDataStorage
import io.circe.{Decoder, Encoder}

trait PatternSpecDataStorageTrait extends FileDataStorage[PatternSpec] {

  override implicit val encoder: Encoder[PatternSpec] =
    ToolPatternsSpecsEncoders.toolPatternEncoder

  override implicit val decoder: Decoder[PatternSpec] =
    ToolPatternsSpecsEncoders.toolPatternDecoder
}

class PatternSpecDataStorage(override val storageFilename: String) extends PatternSpecDataStorageTrait

object PatternSpecDataStorage {

  def apply(storageFilename: String): PatternSpecDataStorage = {
    new PatternSpecDataStorage(storageFilename)
  }
}
