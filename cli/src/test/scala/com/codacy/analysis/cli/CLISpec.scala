package com.codacy.analysis.cli

import better.files.{File, Resource}
import caseapp.Tag
import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.command._
import com.codacy.analysis.core.model.{DuplicationClone, FileError, Result, ToolResult}
import com.codacy.analysis.core.utils.TestUtils._
import com.codacy.plugins.utils.CommandRunner
import io.circe.generic.auto._
import io.circe.parser
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

class CLISpec extends Specification with NoLanguageFeatures {

  private val cli = new MainImpl() {
    override def exit(code: Int): Unit = ()
  }

  "CLIApp" should {
    "parse correctly" in {

      cli.parse(Array()) must beLike {
        case Right((DefaultCommand(_), _, parsed)) => parsed must beNone
      }
      cli.parse(Array("--version")) must beLike {
        case Right((DefaultCommand(_), _, parsed)) => parsed must beNone
      }
      cli.parse(Array("analyse", "--directory", "/tmp", "--tool", "pylint")) must beLike {
        case Right((DefaultCommand(_), _, Some(parsed))) => parsed must beRight
      }
      cli.parse(Array("analyse", "--directory", "/tmp", "--tool", "pylint", "--output", "/tmp/test.txt")) must beLike {
        case Right((DefaultCommand(_), _, Some(parsed))) => parsed must beRight
      }
      cli.parse(Array("analyse", "--directory", "/tmp", "--tool", "pylint", "--verbose")) must beLike {
        case Right((DefaultCommand(_), _, Some(parsed))) => parsed must beRight
      }
      cli.parse(Array("analyse", "--directory", "/tmp", "--tool", "pylint", "--format", "json")) must beLike {
        case Right((DefaultCommand(_), _, Some(parsed))) => parsed must beRight
      }
      cli.parse(
        Array(
          "analyse",
          "--directory",
          "/tmp",
          "--tool",
          "pylint",
          "--commit-uuid",
          "b10790d724e5fd2ca98e8ba3711b6cb10d7f5e38")) must beLike {
        case Right((DefaultCommand(_), _, Some(parsed))) => parsed must beRight
      }
    }

    "fail parse" in {
      cli.parse(Array("bad-command", "--directory", "/tmp", "--tool", "pylint")) must beEqualTo(
        Right(errorMsg("Command not found: bad-command")))
      cli.parse(Array("analyse", "--bad-parameter", "/tmp", "--tool", "pylint")) must beEqualTo(
        Right(errorMsg("Unrecognized argument: --bad-parameter")))
      cli.parse(Array("analyse", "analyse", "--tool-timeout", "1semilha")) must beEqualTo(
        Right(errorMsg("Invalid duration 1semilha (e.g. 20minutes, 10seconds, ...)")))
      cli.parse(Array("analyse", "--directory", "/tmp", "--tool", "pylint", "--commit-uuid", "hold-my-flappy-folds")) must beEqualTo(
        Right(errorMsg("Invalid commit uuid hold-my-flappy-folds - it must be a valid SHA hash")))
    }

    "output text to file" in {
      (for {
        directory <- File.temporaryDirectory()
        file <- File.temporaryFile()
      } yield {
        cli.main(
          Array("analyse", "--directory", directory.pathAsString, "--tool", "pylint", "--output", file.pathAsString))

        file.contentAsString must beEqualTo("""|Starting analysis ...
                                               |Analysis complete
                                               |""".stripMargin)
      }).get()
    }

    "output json to file" in {
      (for {
        directory <- File.temporaryDirectory()
        file <- File.temporaryFile()
      } yield {
        cli.main(
          Array(
            "analyse",
            "--directory",
            directory.pathAsString,
            "--tool",
            "pylint",
            "--format",
            "json",
            "--output",
            file.pathAsString))

        file.contentAsString must beEqualTo("""|[]
                 |""".stripMargin)
      }).get()
    }

    "output correct issues for sample project without remote configuration" in {
      withClonedRepo("git://github.com/qamine-test/codacy-brakeman", "b10790d724e5fd2ca98e8ba3711b6cb10d7f5e38") {
        (file, directory) =>
          cli.main(
            Array(
              "analyse",
              "--directory",
              directory./("src/main/resources/docs/directory-tests/rails4").pathAsString,
              "--tool",
              "brakeman",
              "--format",
              "json",
              "--output",
              file.pathAsString,
              "--verbose"))

          val result = for {
            responseJson <- parser.parse(file.contentAsString)
            response <- responseJson.as[Set[ToolResult]]
            expectedJson <- parser.parse(Resource.getAsString("com/codacy/analysis/cli/cli-output-brakeman-1.json"))
            expected <- expectedJson.as[Set[ToolResult]]
          } yield (response, expected)

          result must beRight
          result must beLike { case Right((response, expected)) => response must beEqualTo(expected) }
      }
    }

    "output correct issues for custom python version" in {
      withClonedRepo(
        "git://github.com/qamine-test/nci-adult-match-treatment-arm-api",
        "38e5ab22774c6061ce693efab4011d49b8feb5ca") { (file, directory) =>
        cli.main(
          Array(
            "analyse",
            "--directory",
            directory.pathAsString,
            "--tool",
            "pylint",
            "--format",
            "json",
            "--output",
            file.pathAsString,
            "--verbose"))

        val result = for {
          responseJson <- parser.parse(file.contentAsString)
          response <- responseJson.as[Set[ToolResult]]
          expectedJson <- parser.parse(Resource.getAsString("com/codacy/analysis/cli/cli-output-pylint-1.json"))
          expected <- expectedJson.as[Set[ToolResult]]
        } yield (response, expected)

        result must beRight
        result must beLike { case Right((response, expected)) => response must beEqualTo(expected) }
        result must beLike {
          case Right((response, _)) => response.exists(_.isInstanceOf[FileError]) must beFalse
        }
      }
    }

    "output correct issues for custom brakeman basedir" in {
      withClonedRepo("git://github.com/qamine-test/codacy-brakeman", "266c56a61d236ed6ee5efa58c0e621125498dd5f") {
        (file, directory) =>
          cli.main(
            Array(
              "analyse",
              "--directory",
              directory.pathAsString,
              "--tool",
              "brakeman",
              "--format",
              "json",
              "--output",
              file.pathAsString,
              "--verbose"))

          val result = for {
            responseJson <- parser.parse(file.contentAsString)
            response <- responseJson.as[Set[ToolResult]]
            expectedJson <- parser.parse(
              Resource.getAsString("com/codacy/analysis/cli/cli-output-brakeman-rails4.json"))
            expected <- expectedJson.as[Set[ToolResult]]
          } yield (response, expected)

          result must beRight
          result must beLike { case Right((response, expected)) => response must beEqualTo(expected) }
      }
    }

    "output correct metrics" in {
      withClonedRepo("git://github.com/qamine-test/codacy-brakeman", "266c56a61d236ed6ee5efa58c0e621125498dd5f") {
        (file, directory) =>
          cli.main(
            Array(
              "analyse",
              "--directory",
              directory.pathAsString,
              "--tool",
              "metrics",
              "--format",
              "json",
              "--output",
              file.pathAsString,
              "--verbose"))

          val result = for {
            responseJson <- parser.parse(file.contentAsString)
            response <- responseJson.as[Set[Result]]
            expectedJson <- parser.parse(
              Resource.getAsString("com/codacy/analysis/cli/cli-output-brakeman-rails-metrics.json"))
            expected <- expectedJson.as[Set[Result]]
          } yield (response, expected)

          result must beRight
          result must beLike { case Right((response, expected)) => response must beEqualTo(expected) }
      }
    }

    "output correct duplication" in {
      withClonedRepo("git://github.com/qamine-test/duplication-delta.git", "625e19cd9be4898939a7c40dbeb2b17e40df9d54") {
        (file, directory) =>
          cli.main(
            Array(
              "analyse",
              "--directory",
              directory.pathAsString,
              "--tool",
              "duplication",
              "--format",
              "json",
              "--output",
              file.pathAsString,
              "--verbose"))

          val result = for {
            responseJson <- parser.parse(file.contentAsString)
            response <- responseJson.as[Set[Result]]
            expectedJson <- parser.parse(Resource.getAsString("com/codacy/analysis/cli/cli-output-duplication.json"))
            expected <- expectedJson.as[Set[Result]]
          } yield (response, expected)

          result must beRight
          result must beLike {
            case Right((response, expected)) =>
              removeCloneLines(response) must beEqualTo(removeCloneLines(expected))
          }
      }
    }

    "fail because of uncommited files with enabled upload" in {
      withTemporaryGitRepo { directory =>
        (for {
          newFile <- File.temporaryFile(parent = Some(directory))
        } yield {

          CommandRunner.exec(List("git", "add", newFile.name), Option(directory.toJava))

          val analyse = Analyse(
            options = CommonOptions(),
            api = APIOptions(projectToken = None, codacyApiBaseUrl = None),
            tool = None,
            directory = Option(directory),
            upload = Tag.of(1),
            extras = ExtraOptions(),
            toolTimeout = None)

          cli.runCommand(analyse) must beEqualTo(ExitStatus.ExitCodes.uncommitedChanges)
        }).get
      }
    }

    "fail because of untracked files with enabled upload" in {
      withTemporaryGitRepo { directory =>
        (for {
          newFile <- File.temporaryFile(parent = Some(directory))
        } yield {

          val analyse = Analyse(
            options = CommonOptions(),
            api = APIOptions(projectToken = None, codacyApiBaseUrl = None),
            tool = None,
            directory = Option(directory),
            upload = Tag.of(1),
            extras = ExtraOptions(),
            toolTimeout = None)

          cli.runCommand(analyse) must beEqualTo(ExitStatus.ExitCodes.uncommitedChanges)
        }).get
      }
    }

    "fail for another reason other than uncommited files (with upload disabled)" in {
      withTemporaryGitRepo { directory =>
        (for {
          newFile <- File.temporaryFile(parent = Some(directory))
        } yield {

          CommandRunner.exec(List("git", "add", newFile.name), Option(directory.toJava))

          val analyse = Analyse(
            options = CommonOptions(),
            api = APIOptions(projectToken = Some("hey, im a token"), codacyApiBaseUrl = Some("https://codacy.com")),
            tool = None,
            directory = Option(directory),
            upload = Tag.of(0),
            extras = ExtraOptions(),
            toolTimeout = None)

          cli.runCommand(analyse) must beEqualTo(ExitStatus.ExitCodes.failedAnalysis)
        }).get
      }
    }

  }

  private def removeCloneLines(resultSet: Set[Result]): Set[DuplicationClone] = {
    resultSet.collect {
      case clone: DuplicationClone =>
        clone.copy(cloneLines = "")
    }
  }

  private def errorMsg(message: String)
    : (DefaultCommand, List[String], Option[Either[String, (String, Command, Seq[String], Seq[String])]]) = {
    (DefaultCommand(), List.empty[String], Some(Left(message)))
  }

}
