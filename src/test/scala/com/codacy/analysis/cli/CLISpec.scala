package com.codacy.analysis.cli

import better.files.File
import com.codacy.analysis.cli.command.{Command, DefaultCommand}
import com.codacy.analysis.cli.model.{FileError, Result}
import com.codacy.analysis.cli.utils.TestUtils._
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
      cli.parse(Array()) must beRight
      cli.parse(Array("--version")) must beRight
      cli.parse(Array("analyse", "--directory", "/tmp", "--tool", "pylint")) must beRight
      cli.parse(Array("analyse", "--directory", "/tmp", "--tool", "pylint", "--output", "/tmp/test.txt")) must beRight
      cli.parse(Array("analyse", "--directory", "/tmp", "--tool", "pylint", "--verbose")) must beRight
      cli.parse(Array("analyse", "--directory", "/tmp", "--tool", "pylint", "--format", "json")) must beRight
    }

    "fail parse" in {
      cli.parse(Array("bad-command", "--directory", "/tmp", "--tool", "pylint")) must beEqualTo(
        Right(errorMsg("Command not found: bad-command")))
      cli.parse(Array("analyse", "--bad-parameter", "/tmp", "--tool", "pylint")) must beEqualTo(
        Right(errorMsg("Unrecognized argument: --bad-parameter")))
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
            response <- responseJson.as[Set[Result]]
            expectedJson <- parser.parse(
              File.resource("com/codacy/analysis/cli/cli-output-brakeman-1.json").contentAsString)
            expected <- expectedJson.as[Set[Result]]
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
          response <- responseJson.as[Set[Result]]
          expectedJson <- parser.parse(
            File.resource("com/codacy/analysis/cli/cli-output-pylint-1.json").contentAsString)
          expected <- expectedJson.as[Set[Result]]
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
            response <- responseJson.as[Set[Result]]
            expectedJson <- parser.parse(
              File.resource("com/codacy/analysis/cli/cli-output-brakeman-rails4.json").contentAsString)
            expected <- expectedJson.as[Set[Result]]
          } yield (response, expected)

          result must beRight
          result must beLike { case Right((response, expected)) => response must beEqualTo(expected) }
      }
    }

  }

  private def errorMsg(message: String)
    : (DefaultCommand, List[String], Option[Either[String, (String, Command, Seq[String], Seq[String])]]) = {
    (DefaultCommand(), List.empty[String], Some(Left(message)))
  }

}
