package com.codacy.analysis.core.git

import better.files.File
import com.codacy.analysis.core.utils.TestUtils._
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

import scala.sys.process.Process
import scala.util.Success

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

    "get all uncommited files without uncommited folders (if an untracked folder contains an untracked file)" in {
      withTemporaryGitRepo { directory =>
        (for {
          file1 <- File.temporaryFile(parent = Some(directory))
          file2 <- File.temporaryFile(parent = Some(directory))
          mainFolder1 <- File.temporaryDirectory(parent = Some(directory))
          subFolder <- File.temporaryDirectory(parent = Some(mainFolder1))
          deepFile <- File.temporaryFile(parent = Some(subFolder))
          mainFolder2 <- File.temporaryDirectory(parent = Some(directory))
          noContentsFolder <- File.temporaryDirectory(parent = Some(mainFolder2))
        } yield {
          Git.repository(directory).flatMap(_.uncommitedFiles) must beLike {
            case Success(uncommited) =>
              uncommited must containTheSameElementsAs(Seq(file1, file2, deepFile).map(relativePath(_, directory)))
              uncommited must not contain relativePath(noContentsFolder, directory)
          }
        }).get
      }
    }

  }

  private def relativePath(targetFile: File, directory: File): String = {
    targetFile.pathAsString.replace(s"${directory.pathAsString}/", "")
  }
}
