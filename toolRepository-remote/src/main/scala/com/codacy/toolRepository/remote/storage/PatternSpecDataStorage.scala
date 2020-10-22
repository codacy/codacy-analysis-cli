package com.codacy.toolRepository.remote.storage

import com.codacy.analysis.core.model.PatternSpec
import com.codacy.analysis.core.storage.FileDataStorage
import io.circe.{Decoder, Encoder}

case class PatternSpecDataStorage(override val storageFilename: String)
    extends FileDataStorage[PatternSpec](storageFilename) {

  override implicit val encoder: Encoder[PatternSpec] =
    ToolPatternsSpecsEncoders.toolPatternEncoder

  override implicit val decoder: Decoder[PatternSpec] =
    ToolPatternsSpecsEncoders.toolPatternDecoder
}
