package com.codacy.analysis.core.utils

import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Path, Paths}

import better.files.File
import com.codacy.plugins.api.results
import io.circe.Decoder
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.MatchResult

import scala.sys.process.Process

object TestUtils {
  implicit val categoryDecoder: Decoder[results.Pattern.Category.Value] = Decoder.enumDecoder(results.Pattern.Category)
  implicit val levelDecoder: Decoder[results.Result.Level.Value] = Decoder.enumDecoder(results.Result.Level)
  implicit val fileDecoder: Decoder[Path] = Decoder[String].map(Paths.get(_))
  implicit val executionEnv: ExecutionEnv = ExecutionEnv.fromGlobalExecutionContext

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

  def withTemporaryGitRepo[T](fn: (File, File, File, File) => MatchResult[T]): MatchResult[T] = {
    (for {
      temporaryDirectory <- File.temporaryDirectory()
      temporaryFile1 <- File.temporaryFile(parent = Some(temporaryDirectory))
      temporaryFile2 <- File.temporaryFile(parent = Some(temporaryDirectory))
      temporaryFile3 <- File.temporaryFile(parent = Some(temporaryDirectory))
    } yield {

      def addFile(file: File) = {
        Process(Seq("git", "add", temporaryDirectory.relativize(file).toString), temporaryDirectory.toJava).!
      }

      Process(Seq("git", "init"), temporaryDirectory.toJava).!
      addFile(temporaryFile1)
      addFile(temporaryFile2)
      addFile(temporaryFile3)
      Process(Seq("git", "commit", "-m", "tmp"), temporaryDirectory.toJava).!

      fn(temporaryDirectory, temporaryFile1, temporaryFile2, temporaryFile3)
    }).get

  }
}
