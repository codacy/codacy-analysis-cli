package com.codacy.analysis.core.files

import better.files.File
import com.codacy.analysis.core.utils.IOHelper.IOThrowable
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification
import scalaz.zio.{IO, RTS}

import scala.util.Try

class FallbackFileCollectorSpec extends Specification with NoLanguageFeatures with RTS {

  private val failingCompanion: FileCollectorCompanion[IOThrowable] = new FileCollectorCompanion[IOThrowable] {
    override def name: String = ""

    override def apply(): FileCollector[IOThrowable] = new FileCollector[IOThrowable] {
      override def list(directory: File): IOThrowable[FilesTarget] = {
        IO.fail(new Exception("because fail"))
      }
    }
  }

  private val successfulCompanion: FileCollectorCompanion[IOThrowable] = new FileCollectorCompanion[IOThrowable] {
    override def name: String = ""

    override def apply(): FileCollector[IOThrowable] = new FileCollector[IOThrowable] {
      override def list(directory: File): IOThrowable[FilesTarget] = {
        IO.now(FilesTarget(directory, Set.empty, Set.empty))
      }
    }
  }

  "FallbackFileCollectorSpec" should {
    "not fallback" in {
      Try(unsafeRun(new FallbackFileCollector(List(successfulCompanion, failingCompanion)).list(File("")))) must beSuccessfulTry
    }

    "fallback" in {
      Try(unsafeRun(new FallbackFileCollector(List(failingCompanion, successfulCompanion)).list(File("")))) must beSuccessfulTry
    }

    "fail when all fail" in {
      Try(unsafeRun(new FallbackFileCollector(List(failingCompanion, failingCompanion)).list(File("")))) must beFailedTry
    }
  }
}
