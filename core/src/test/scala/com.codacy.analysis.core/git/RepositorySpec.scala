package com.codacy.analysis.core.git

import better.files.File
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

import scala.sys.process.Process

class RepositorySpec extends Specification with NoLanguageFeatures {

  "Repository" should {
    "get latest commit" in {

      "when it exists" in {
        (for {
          temporaryDirectory <- File.temporaryDirectory()
          temporaryFile <- File.temporaryFile(parent = Some(temporaryDirectory))
        } yield {
          Process(Seq("git", "init"), temporaryDirectory.toJava).!
          Process(Seq("git", "add", temporaryDirectory.relativize(temporaryFile).toString), temporaryDirectory.toJava).!
          Process(Seq("git", "commit", "-m", "tmp"), temporaryDirectory.toJava).!

          Git.repository(temporaryDirectory).flatMap(_.latestCommit) must beSuccessfulTry
        }).get
      }

      "when it doesn't exist" in {
        (for {
          temporaryDirectory <- File.temporaryDirectory()
        } yield {
          Process(Seq("git", "init"), temporaryDirectory.toJava).!

          Git.repository(temporaryDirectory).flatMap(_.latestCommit) must beFailedTry
        }).get
      }
    }

  }
}
