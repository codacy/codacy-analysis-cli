package com.codacy.analysis.core.git

import better.files.File
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification
import com.codacy.analysis.core.utils.TestUtils._

import scala.sys.process.Process
import scala.util.Success

class CommitSpec extends Specification with NoLanguageFeatures {

  "Commit" should {
    "get all files" in {
      withTemporaryGitRepo { temporaryDirectory =>
        (for {
          tempFile1 <- File.temporaryFile(parent = Some(temporaryDirectory))
          tempFile2 <- File.temporaryFile(parent = Some(temporaryDirectory))
          tempFile3 <- File.temporaryFile(parent = Some(temporaryDirectory))
        } yield {

          def addFile(file: File) = {
            Process(Seq("git", "add", temporaryDirectory.relativize(file).toString), temporaryDirectory.toJava).!
          }
          addFile(tempFile1)
          addFile(tempFile2)
          addFile(tempFile3)
          Process(Seq("git", "commit", "-m", "tmp"), temporaryDirectory.toJava).!

          val expectedFiles =
            List(tempFile1, tempFile2, tempFile3).map(temporaryDirectory.relativize)
          Git.repository(temporaryDirectory).flatMap(_.latestCommit).flatMap(_.files) must beLike {
            case Success(fileSet) => fileSet must containTheSameElementsAs(expectedFiles)
          }
        }).get()
      }
    }
  }
}
