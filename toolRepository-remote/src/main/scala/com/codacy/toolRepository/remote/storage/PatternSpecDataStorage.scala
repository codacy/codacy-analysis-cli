package com.codacy.toolRepository.remote.storage

import com.codacy.analysis.core.model.PatternSpec
import com.codacy.analysis.core.storage.FileDataStorage
import io.circe.{Decoder, Encoder}
import better.files.File

case class PatternSpecDataStorage(override val currentWorkingDirectory: File, override val storageFilename: String)
    extends FileDataStorage[PatternSpec](currentWorkingDirectory, storageFilename) {

  override implicit val encoder: Encoder[PatternSpec] =
    ToolPatternsSpecsEncoders.toolPatternEncoder

  override implicit val decoder: Decoder[PatternSpec] =
    ToolPatternsSpecsEncoders.toolPatternDecoder
}
