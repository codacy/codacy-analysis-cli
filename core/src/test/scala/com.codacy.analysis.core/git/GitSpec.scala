package com.codacy.analysis.core.git

import better.files.File
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

import scala.sys.process.Process

class GitSpec extends Specification with NoLanguageFeatures {

  "Git" should {
    "create a repository" in {
      (for {
        temporaryDirectory <- File.temporaryDirectory()
      } yield {
        Process(Seq("git", "init"), temporaryDirectory.toJava).!

        Git.repository(temporaryDirectory) must beSuccessfulTry
      }).get
    }

    "fail to create a repository" in {
      (for {
        temporaryDirectory <- File.temporaryDirectory()
      } yield {
        Git.repository(temporaryDirectory) must beFailedTry
      }).get
    }
  }

}
