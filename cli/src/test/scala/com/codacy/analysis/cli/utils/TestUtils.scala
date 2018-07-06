package com.codacy.analysis.cli.utils

import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Path, Paths}

import better.files.File
import com.codacy.plugins.api
import io.circe.Decoder
import org.specs2.matcher.MatchResult

import scala.sys.process.Process

object TestUtils {
  implicit val categoryDecoder: Decoder[api.results.Pattern.Category.Value] =
    Decoder.enumDecoder(api.results.Pattern.Category)
  implicit val levelDecoder: Decoder[api.results.Result.Level.Value] = Decoder.enumDecoder(api.results.Result.Level)
  implicit val fileDecoder: Decoder[Path] = Decoder[String].map(Paths.get(_))

  def withClonedRepo[T](gitUrl: String, commitUUid: String)(block: (File, File) => MatchResult[T]): MatchResult[T] =
    (for {
      directory <- File.temporaryDirectory()
      file <- File.temporaryFile()
    } yield {
      directory
        .addPermission(PosixFilePermission.OWNER_READ)
        .addPermission(PosixFilePermission.GROUP_READ)
        .addPermission(PosixFilePermission.OTHERS_READ)
        .addPermission(PosixFilePermission.OWNER_EXECUTE)
        .addPermission(PosixFilePermission.GROUP_EXECUTE)
        .addPermission(PosixFilePermission.OTHERS_EXECUTE)
      Process(Seq("git", "clone", gitUrl, directory.pathAsString)).!
      Process(Seq("git", "reset", "--hard", commitUUid), directory.toJava).!
      block(file, directory)
    }).get()

}
