package com.codacy.analysis.core.git

import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification
import com.codacy.analysis.core.utils.TestUtils._
import scala.util.Success

class CommitSpec extends Specification with NoLanguageFeatures {

  "Commit" should {
    "get all files" in {
      withTemporaryGitRepo { (temporaryDirectory, tempFile1, tempFile2, tempFile3) =>
        val expectedFiles = List(tempFile1, tempFile2, tempFile3).map(temporaryDirectory.relativize)
        Git.repository(temporaryDirectory).flatMap(_.latestCommit).flatMap(_.files) must beLike {
          case Success(fileSet) => fileSet must containTheSameElementsAs(expectedFiles)
        }
      }
    }
  }
}
