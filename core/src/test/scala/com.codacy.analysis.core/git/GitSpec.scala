package com.codacy.analysis.core.git

import better.files.File
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification
import com.codacy.analysis.core.utils.TestUtils._
import scalaz.zio.RTS

import scala.sys.process.Process
import scala.util.{Success, Try}

class GitSpec extends Specification with NoLanguageFeatures with RTS {

  "Git" should {
    "create a repository" in {
      (for {
        temporaryDirectory <- File.temporaryDirectory()
      } yield {
        Process(Seq("git", "init"), temporaryDirectory.toJava).!

        Try(unsafeRun(Git.repository(temporaryDirectory))) must beSuccessfulTry
      }).get
    }

    "get the current commit uuid" in {
      withTemporaryGitRepo(directory => {
        val expectedUuid = Process(Seq("git", "rev-parse", "HEAD"), directory.toJava).!!.trim
        Try(unsafeRun(Git.currentCommitUuid(directory))) must beLike {
          case Success(commit) => commit.value must beEqualTo(expectedUuid)
        }
      })
    }

    "fail to create a repository" in {
      (for {
        temporaryDirectory <- File.temporaryDirectory()
      } yield {
        Try(unsafeRun(Git.repository(temporaryDirectory))) must beFailedTry
      }).get
    }
  }

}
