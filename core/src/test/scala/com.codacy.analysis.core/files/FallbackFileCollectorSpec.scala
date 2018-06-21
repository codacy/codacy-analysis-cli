package com.codacy.analysis.cli.files

import better.files.File
import com.codacy.analysis.core.clients.api.ProjectConfiguration
import com.codacy.analysis.core.configuration.CodacyConfigurationFile
import com.codacy.analysis.core.files.{FallbackFileCollector, FileCollector, FileCollectorCompanion, FilesTarget}
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

import scala.util.{Failure, Success, Try}

class FallbackFileCollectorSpec extends Specification with NoLanguageFeatures {

  val failingCompanion = new FileCollectorCompanion[Try] {
    override def name: String = ""

    override def apply(): FileCollector[Try] = new FileCollector[Try] {
      override def list(directory: File,
                        localConfiguration: Either[String, CodacyConfigurationFile],
                        remoteConfiguration: Either[String, ProjectConfiguration]): Try[FilesTarget] = {
        Failure(new Exception("because fail"))
      }
    }
  }

  val successfulCompanion = new FileCollectorCompanion[Try] {
    override def name: String = ""

    override def apply(): FileCollector[Try] = new FileCollector[Try] {
      override def list(directory: File,
                        localConfiguration: Either[String, CodacyConfigurationFile],
                        remoteConfiguration: Either[String, ProjectConfiguration]): Try[FilesTarget] = {
        Success(FilesTarget(directory, Set.empty, Set.empty))
      }
    }
  }

  "FallbackFileCollectorSpec" should {
    "not fallback" in {
      new FallbackFileCollector(List(successfulCompanion, failingCompanion))
        .list(new File(""), Left(""), Left("")) must beLike {
        case Success(filesTarget) => filesTarget must not beNull
      }
    }

    "fallback" in {
      new FallbackFileCollector(List(failingCompanion, successfulCompanion))
        .list(new File(""), Left(""), Left("")) must beLike {
        case Success(filesTarget) => filesTarget must not beNull
      }
    }

    "fail when all fail" in {
      new FallbackFileCollector(List(failingCompanion, failingCompanion))
        .list(new File(""), Left(""), Left("")) must beLike {
        case Failure(e) => e must not beNull
      }
    }
  }
}
