package com.codacy.analysis.cli.command

import better.files.File
import caseapp._
import caseapp.core.ArgParser
import com.codacy.analysis.cli.command.ArgumentParsers._
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.cli.model.{FileError, Issue, LineLocation}
import com.codacy.analysis.cli.utils
import org.log4s.{Logger, getLogger}

abstract class CLIApp extends CommandAppWithBaseCommand[DefaultCommand, Command] {
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

object Version {
  val version: String = Option(getClass.getPackage.getImplementationVersion).getOrElse("0.1.0-SNAPSHOT")
}

@AppName("Codacy Analysis Cli")
@AppVersion(Version.version)
@ProgName("codacy-analysis-cli")
final case class DefaultCommand(
  @ExtraName("v") @ValueDescription("Prints the version of the program")
  version: Option[Unit])
    extends Runnable {

  def run(): Unit = {
    if (version.isDefined) {
      Console.println(s"codacy-analysis-cli is on version ${Version.version}")
    }
  }
}

final case class CommonOptions(
  @ValueDescription("Run the tool with verbose output")
  verbose: Option[Unit] = Option.empty)

sealed trait Command extends Runnable {
  def options: CommonOptions
}

final case class Analyse(@Recurse
                         options: CommonOptions,
                         @ExtraName("t") @ValueDescription("The tool to analyse on the code")
                         tool: Option[String],
                         @ExtraName("d") @ValueDescription("The directory to be analysed")
                         directory: Option[File],
                         @ExtraName("f") @ValueDescription("The format to output")
                         format: String = "text")
    extends Command {

  def run(): Unit = {
    implicit val logger: Logger = utils.Logger.withLevel(getLogger, options.verbose.isDefined)

    val formatter = Formatter(format)

    formatter.begin()
    formatter.add(FileError("filename", "message"))
    formatter.add(Issue(LineLocation(1), "filename"))
    formatter.end()
  }
}

object ArgumentParsers {
  implicit val fileParser: ArgParser[File] = {
    ArgParser.instance[File]("file") { path: String =>
      Option(File(path)).filter(_.exists).fold[Either[String, File]](Left(s"The path $path does not exist"))(Right(_))
    }
  }

  implicit val boolean: ArgParser[Option[Unit]] = {
    ArgParser.flag("flag")(_ => Right(Option(())))
  }
}
