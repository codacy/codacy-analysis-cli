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

    "get all uncommitted changes" in {

      "changed files" in {
        withTemporaryGitRepo { directory =>
          val file = directory / "random_file.file"
          file.createFileIfNotExists(createParents = true)

          Process(Seq("git", "add", "."), directory.toJava).!
          Process(Seq("git", "commit", "-m", "added a new file!"), directory.toJava).!

          file.write("Random file contents")

          Git.repository(directory).flatMap(_.uncommitedFiles) must beLike {
            case Success(uncommited) =>
              uncommited must contain(exactly(relativePath(file, directory)))
          }
        }
      }

      "untracked files" in {
        "with an untracked folder that contains an untracked file" in {
          withTemporaryGitRepo { directory =>
            val deepFile = directory / "mainFolder" / "subFolder" / "deepFile.sc"
            deepFile.createFileIfNotExists(createParents = true)

            Git.repository(directory).flatMap(_.uncommitedFiles) must beLike {
              case Success(uncommited) =>
                uncommited must contain(exactly(relativePath(deepFile, directory)))
            }
          }
        }

        "with an untracked folder with no content" in {
          withTemporaryGitRepo { directory =>
            val noContentsFolder = directory / "mainFolder" / "noContents"
            noContentsFolder.createDirectoryIfNotExists(createParents = true)

            Git.repository(directory).flatMap(_.uncommitedFiles) must beLike {
              case Success(uncommited) =>
                uncommited must beEmpty
            }
          }
        }
      }
    }

  }

  private def relativePath(targetFile: File, directory: File): String = {
    targetFile.pathAsString.stripPrefix(s"${directory.pathAsString}/")
  }
}
