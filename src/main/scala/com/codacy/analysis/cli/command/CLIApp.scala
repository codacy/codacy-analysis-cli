package com.codacy.analysis.cli.command

import better.files.File
import caseapp._
import caseapp.core.ArgParser
import com.codacy.analysis.cli.analysis.Analyser
import com.codacy.analysis.cli.command.ArgumentParsers._
import com.codacy.analysis.cli.formatter.Formatter

abstract class CLIApp extends CommandAppWithBaseCommand[DefaultCommand, Command] {
  def run(command: Command): Unit

  override final def run(command: Command, remainingArgs: RemainingArgs): Unit = {
    run(command)
  }

  override def defaultCommand(command: DefaultCommand, remainingArgs: Seq[String]): Unit = {
    if (command.version.## > 0) {
      command.run()
    } else {
      helpAsked()
    }
  }
}

object ArgumentParsers {
  implicit val fileParser: ArgParser[File] = {
    ArgParser.instance[File]("file") { path: String =>
      Right(File(path))
    }
  }
}

object Version {
  val version: String = Option(getClass.getPackage.getImplementationVersion).getOrElse("0.1.0-SNAPSHOT")
}

object Properties {
  val codacyCode: Option[File] = sys.env.get("CODACY_CODE").map(File(_))
}

@AppName("Codacy Analysis Cli")
@AppVersion(Version.version)
@ProgName("codacy-analysis-cli")
final case class DefaultCommand(
  @ExtraName("v") @ValueDescription("Prints the version of the program")
  version: Int @@ Counter = Tag.of(0))
    extends Runnable {

  def run(): Unit = {
    if (version.## > 0) {
      Console.println(s"codacy-analysis-cli is on version ${Version.version}")
    }
  }
}

final case class CommonOptions(
  @ValueDescription("Run the tool with verbose output")
  verbose: Int @@ Counter = Tag.of(0))

sealed trait Command {
  def options: CommonOptions
}

final case class APIOptions(@ValueDescription("The project token.")
                            projectToken: Option[String],
                            @ValueDescription("The api token.")
                            apiToken: Option[String],
                            @ValueDescription("The username.")
                            username: Option[String],
                            @ValueDescription("The project name.")
                            project: Option[String],
                            @ValueDescription("The codacy api base url.")
                            codacyApiBaseUrl: Option[String])

final case class ExtraOptions(
  @Hidden @ValueDescription(s"The analyser to use. (${Analyser.allAnalysers.map(_.name).mkString(", ")})")
  analyser: String = Analyser.defaultAnalyser.name)

final case class Analyse(@Recurse
                         options: CommonOptions,
                         @Recurse
                         api: APIOptions,
                         @ExtraName("t") @ValueDescription("The tool to analyse the code.")
                         tool: String,
                         @ExtraName("d") @ValueDescription("The directory to analyse.")
                         directory: Option[File],
                         @ExtraName("f") @ValueDescription(
                           s"The output format. (${Formatter.allFormatters.map(_.name).mkString(", ")})")
                         format: String = Formatter.defaultFormatter.name,
                         @ExtraName("o") @ValueDescription("The output destination file.")
                         output: Option[File] = Option.empty,
                         @Recurse
                         extras: ExtraOptions)
    extends Command
