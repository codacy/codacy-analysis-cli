package com.codacy.analysis.cli

import better.files.File
import caseapp.RemainingArgs
import com.codacy.analysis.cli.command.ArgumentParsers._
import com.codacy.analysis.cli.command.{Command, CommandAppWithBaseCommand, DefaultCommand}
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

class CleanerSpec extends Specification with NoLanguageFeatures {

  private val cli = new CommandAppWithBaseCommand[DefaultCommand, Command] {
    override def exit(code: Int): Unit = ()

    override final def run(command: Command, remainingArgs: RemainingArgs): Unit = {
      command.run()
    }

    override def defaultCommand(command: DefaultCommand, remainingArgs: Seq[String]): Unit = {
      if (command.version.isDefined) {
        command.run()
      } else {
        helpAsked()
      }
    }
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
        file <- File.temporaryFile()
      } yield {
        cli.main(Array("analyse", "--directory", "/tmp", "--tool", "pylint", "--output", file.pathAsString))

        file.contentAsString must beEqualTo("""|Starting analysis ...
                                               |Analysis complete
                                               |""".stripMargin)
      }).get()
    }

    "output json to file" in {
      (for {
        file <- File.temporaryFile()
      } yield {
        cli.main(
          Array(
            "analyse",
            "--directory",
            "/tmp",
            "--tool",
            "pylint",
            "--format",
            "json",
            "--output",
            file.pathAsString))

        file.contentAsString must beEqualTo(
          """|[]
             |""".stripMargin)
      }).get()
    }

  }

  private def errorMsg(message: String)
    : (DefaultCommand, List[String], Option[Either[String, (String, Command, Seq[String], Seq[String])]]) = {
    (DefaultCommand(None), List.empty[String], Some(Left(message)))
  }

}
