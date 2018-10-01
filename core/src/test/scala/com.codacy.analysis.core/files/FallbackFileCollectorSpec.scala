package com.codacy.analysis.core.files

import better.files.File
import cats.instances.try_.catsStdInstancesForTry
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

import scala.util.{Failure, Success, Try}

class FallbackFileCollectorSpec extends Specification with NoLanguageFeatures {

  private val failingCompanion: FileCollectorCompanion[Try] = new FileCollectorCompanion[Try] {
    override def name: String = ""

    override def apply(): FileCollector[Try] = new FileCollector[Try] {
      override def list(directory: File): Try[FilesTarget] = {
        Failure(new Exception("because fail"))
      }
    }
  }

  private val successfulCompanion: FileCollectorCompanion[Try] = new FileCollectorCompanion[Try] {
    override def name: String = ""

    override def apply(): FileCollector[Try] = new FileCollector[Try] {
      override def list(directory: File): Try[FilesTarget] = {
        Success(FilesTarget(directory, Set.empty, Set.empty))
      }
    }
  }

  def tryFallbackFileCollector(fileCollectorCompanions: List[FileCollectorCompanion[Try]]): FileCollector[Try] = {
    new FallbackFileCollectorCompanion[Try, Throwable](fileCollectorCompanions)(new Exception(_)).apply()
  }

  "FallbackFileCollectorSpec" should {
    "not fallback" in {
      tryFallbackFileCollector(List(successfulCompanion, failingCompanion)).list(File("")) must beSuccessfulTry
    }

    "fallback" in {
      tryFallbackFileCollector(List(failingCompanion, successfulCompanion)).list(File("")) must beSuccessfulTry
    }

    "fail when all fail" in {
      tryFallbackFileCollector(List(failingCompanion, failingCompanion)).list(File("")) must beFailedTry
    }
  }
}
