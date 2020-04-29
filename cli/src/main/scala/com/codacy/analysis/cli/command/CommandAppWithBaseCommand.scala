package com.codacy.analysis.cli.command

import caseapp.core.{CommandsMessages, Messages, WithHelp}
import caseapp.{CommandParser, Parser, RemainingArgs}
import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.analysis.ExitStatus.ExitCodes

abstract class CommandAppWithBaseCommand[D, T](implicit
  val beforeCommandParser: Parser[D],
  baseBeforeCommandMessages: Messages[D],
  val commandParser: CommandParser[T],
  val commandsMessages: CommandsMessages[T]) {

  def defaultCommand(options: D, remainingArgs: Seq[String]): Unit

  def run(options: T, remainingArgs: RemainingArgs): ExitStatus.ExitCode

  def exit(code: ExitStatus.ExitCode): Unit =
    sys.exit(code.value)

  def error(message: String): Unit = {
    Console.err.println(message)
    exit(ExitCodes.genericError)
  }

  lazy val beforeCommandMessages: Messages[D] = baseBeforeCommandMessages.copy(
    appName = appName,
    appVersion = appVersion,
    progName = progName,
    optionsDesc = "[options] [command] [command-options]")

  lazy val commands: Seq[String] = CommandsMessages[T].messages.map { case (c, _) => c }

  def helpAsked(): Unit = {
    print(beforeCommandMessages.helpMessage)
    println(s"Available commands: ${commands.mkString(", ")}\n")
    println(s"Type  $progName command --help  for help on an individual command")
    exit(ExitCodes.success)
  }

  def commandHelpAsked(command: String): Unit = {
    println(commandsMessages.messagesMap(command).helpMessage(beforeCommandMessages.progName, command))
    exit(ExitCodes.success)
  }

  def usageAsked(): Unit = {
    println(beforeCommandMessages.usageMessage)
    println(s"Available commands: ${commands.mkString(", ")}\n")
    println(s"Type  $progName command --usage  for usage of an individual command")
    exit(ExitCodes.success)
  }

  def commandUsageAsked(command: String): Unit = {
    println(commandsMessages.messagesMap(command).usageMessage(beforeCommandMessages.progName, command))
    exit(ExitCodes.success)
  }

  def appName: String = Messages[D].appName
  def appVersion: String = Messages[D].appVersion
  def progName: String = Messages[D].progName

  def parse(args: Array[String])
    : Either[String, (D, Seq[String], Option[Either[String, (String, T, Seq[String], Seq[String])]])] = {
    commandParser.detailedParse(args)
  }

  def main(args: Array[String]): Unit = {
    commandParser.withHelp.detailedParse(args)(beforeCommandParser.withHelp) match {
      case Left(err) =>
        error(err)

      case Right((WithHelp(usage, help, d), dArgs, optCmd)) =>
        if (help)
          helpAsked()

        if (usage)
          usageAsked()

        d match {
          case Left(err) =>
            error(err)
          case Right(d0) =>
            if (optCmd.isEmpty) {
              defaultCommand(d0, dArgs)
            }
        }

        optCmd.foreach {
          case Left(err) =>
            error(err)

          case Right((c, WithHelp(commandUsage, commandHelp, t), commandArgs, commandArgs0)) =>
            if (commandHelp)
              commandHelpAsked(c)

            if (commandUsage)
              commandUsageAsked(c)

            t match {
              case Left(err) =>
                error(err)
              case Right(t0) =>
                val code = run(t0, RemainingArgs(commandArgs, commandArgs0))
                exit(code)
            }
        }
    }
  }

}
