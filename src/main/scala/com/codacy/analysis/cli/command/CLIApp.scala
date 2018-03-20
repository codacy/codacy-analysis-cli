package com.codacy.analysis.cli.command

import better.files.File
import caseapp._
import caseapp.core.ArgParser
import com.codacy.analysis.cli.command.ArgumentParsers._
import com.codacy.analysis.cli.command.analyse.Analyser
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.cli.model.FileCfg
import com.codacy.analysis.cli.utils
import org.log4s.{Logger, getLogger}

import scala.util.{Failure, Success, Try}

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

object ArgumentParsers {
  implicit val fileParser: ArgParser[File] = {
    ArgParser.instance[File]("file") { path: String =>
      Right(File(path))
    }
  }

  implicit val boolean: ArgParser[Option[Unit]] = {
    ArgParser.flag("flag") {
      case Some(_) => Right(Option(()))
      case None    => Right(Option.empty)
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
                         @ExtraName("t") @ValueDescription("The tool to analyse on the code.")
                         tool: String,
                         @Hidden @ExtraName("d") @ValueDescription("The directory to be analysed.")
                         directory: File = Properties.codacyCode.getOrElse(File.currentWorkingDirectory),
                         @ExtraName("f") @ValueDescription(
                           s"The format to output. (${Formatter.allFormatters.map(_.name).mkString(", ")})")
                         format: String = Formatter.defaultFormatter.name,
                         @ExtraName("o") @ValueDescription("The output file destination.")
                         output: Option[File] = Option.empty,
                         @Hidden @ExtraName("a") @ValueDescription(
                           s"The analyser to use. (${Analyser.allAnalysers.map(_.name).mkString(", ")})")
                         analyser: String = Analyser.defaultAnalyser.name)
    extends Command {

  private implicit val logger: Logger = utils.Logger.withLevel(getLogger, options.verbose.isDefined)
  private val formatterImpl: Formatter = Formatter(format, output)
  private val analyserImpl: Analyser[Try] = Analyser(analyser)

  def run(): Unit = {
    formatterImpl.begin()

    analyserImpl.analyse(tool, directory, FileCfg) match {
      case Success(res) =>
        logger.info(s"Completed analysis for $tool")
        res.foreach(formatterImpl.add)
      case Failure(e) =>
        logger.error(e)(s"Failed to run analysis for $tool")
    }

    formatterImpl.end()
  }
}
