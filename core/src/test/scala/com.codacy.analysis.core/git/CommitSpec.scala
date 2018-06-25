package com.codacy.analysis.core.git

import better.files.File
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

import scala.sys.process.Process
import scala.util.Success

class CommitSpec extends Specification with NoLanguageFeatures {

  "Commit" should {
    "get all files" in {
      (for {
        temporaryDirectory <- File.temporaryDirectory()
        temporaryFile1 <- File.temporaryFile(parent = Some(temporaryDirectory))
        temporaryFile2 <- File.temporaryFile(parent = Some(temporaryDirectory))
        temporaryFile3 <- File.temporaryFile(parent = Some(temporaryDirectory))
      } yield {
        val expectedFiles = List(temporaryFile1, temporaryFile2, temporaryFile3).map(temporaryDirectory.relativize)

        def addFile(file: File) = {
          Process(Seq("git", "add", temporaryDirectory.relativize(file).toString), temporaryDirectory.toJava).!
        }

        Process(Seq("git", "init"), temporaryDirectory.toJava).!
        addFile(temporaryFile1)
        addFile(temporaryFile2)
        addFile(temporaryFile3)
        Process(Seq("git", "commit", "-m", "tmp"), temporaryDirectory.toJava).!

        Git.repository(temporaryDirectory).flatMap(_.latestCommit).flatMap(_.files) must beLike {
          case Success(fileSet) => fileSet must containTheSameElementsAs(expectedFiles)
        }
      }).get
    }
  }
}
