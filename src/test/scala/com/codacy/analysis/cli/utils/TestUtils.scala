package com.codacy.analysis.cli.utils

import java.nio.file.{Path, Paths}

import better.files.File
import codacy.docker.api
import io.circe.Decoder
import org.specs2.matcher.MatchResult

import scala.sys.process.Process

object TestUtils {
  implicit val categoryDecoder: Decoder[api.Pattern.Category.Value] = Decoder.enumDecoder(api.Pattern.Category)
  implicit val levelDecoder: Decoder[api.Result.Level.Value] = Decoder.enumDecoder(api.Result.Level)
  implicit val fileDecoder: Decoder[Path] = Decoder[String].map(Paths.get(_))

  def withClonedRepo[T](gitUrl: String, commitUUid: String)(block: (File, File) => MatchResult[T]): MatchResult[T] =
    (for {
      directory <- File.temporaryDirectory()
      file <- File.temporaryFile()
    } yield {
      Process(Seq("git", "clone", gitUrl, directory.pathAsString)).!
      Process(Seq("git", "reset", "--hard", commitUUid), directory.toJava).!
      block(file, directory)
    }).get()
}
