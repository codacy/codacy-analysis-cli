package com.codacy.analysis.core.git

import better.files.File
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification
import com.codacy.analysis.core.utils.TestUtils._

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

    "get the current commit uuid" in {
      withTemporaryGitRepo(directory => {
        val expectedUuid = Process(Seq("git", "rev-parse", "HEAD"), directory.toJava).!!.trim
        Git.currentCommitUuid(directory) must beLike {
          case Some(commit) => commit.value must beEqualTo(expectedUuid)
        }
      })
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
