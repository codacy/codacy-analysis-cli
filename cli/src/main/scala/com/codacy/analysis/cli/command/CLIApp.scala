package com.codacy.analysis.cli.command

import better.files.File
import caseapp._
import caseapp.core.ArgParser
import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.command.ArgumentParsers._
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.core.analysis.Analyser
import com.codacy.analysis.core.clients.{ProjectName, UserName}
import com.codacy.analysis.core.git.Commit

import scala.concurrent.duration.Duration
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

abstract class CLIApp extends CommandAppWithBaseCommand[DefaultCommand, CommandOptions] {
  def run(command: CommandOptions): ExitStatus.ExitCode

  override final def run(command: CommandOptions, remainingArgs: RemainingArgs): ExitStatus.ExitCode = {
    run(command)
  }

  override def defaultCommand(command: DefaultCommand, remainingArgs: Seq[String]): Unit = {
    if (command.versionValue) {
      command.run()
    } else {
      helpAsked()
    }
  }
}

object ArgumentParsers {

  private val commitUuidRegex: Regex = "^[a-fA-F0-9]{40}$".r

  implicit val fileParser: ArgParser[File] = {
    ArgParser.instance[File]("file") { path: String =>
      Right(File(path))
    }
  }

  implicit val userNameParser: ArgParser[UserName] = {
    ArgParser.instance[UserName]("username") { path: String =>
      Right(UserName(path))
    }
  }

  implicit val projectNameParser: ArgParser[ProjectName] = {
    ArgParser.instance[ProjectName]("project") { path: String =>
      Right(ProjectName(path))
    }
  }

  implicit val durationParser: ArgParser[Duration] = {
    ArgParser.instance[Duration]("duration") { duration: String =>
      Try(Duration(duration)) match {
        case Success(d) => Right(d)
        case Failure(_) => Left(s"Invalid duration $duration (e.g. 20minutes, 10seconds, ...)")
      }
    }
  }

  implicit val commitUuidParser: ArgParser[Commit.Uuid] = {
    ArgParser.instance[Commit.Uuid]("commitUuid") { commitUuid: String =>
      commitUuidRegex.findFirstIn(commitUuid) match {
        case Some(uuid) => Right(Commit.Uuid(uuid))
        case None       => Left(s"Invalid commit uuid $commitUuid - it must be a valid SHA hash")
      }
    }
  }
}

object Version {

  val version: String =
    Option(getClass.getPackage.getImplementationVersion).getOrElse("0.1.0-SNAPSHOT")
}

@AppName("Codacy Analysis Cli")
@AppVersion(Version.version)
@ProgName("codacy-analysis-cli")
final case class DefaultCommand(
  @ExtraName("v") @ValueDescription("Prints the version of the program")
  version: Int @@ Counter = Tag.of(0))
    extends Runnable {

  val versionValue: Boolean = version.## > 0

  def run(): Unit = {
    if (versionValue) {
      Console.println(s"codacy-analysis-cli is on version ${Version.version}")
    }
  }
}

final case class CommonOptions(
  @ValueDescription("Run the tool with verbose output")
  verbose: Int @@ Counter = Tag.of(0)) {
  val verboseValue: Boolean = verbose.## > 0
}

sealed trait CommandOptions {
  def options: CommonOptions
}

final case class APIOptions(@ValueDescription("The project token.")
                            projectToken: Option[String] = Option.empty,
                            @ValueDescription("The api token.")
                            apiToken: Option[String] = Option.empty,
                            @ValueDescription("The username.")
                            username: Option[UserName] = Option.empty,
                            @ValueDescription("The project name.")
                            project: Option[ProjectName] = Option.empty,
                            @ValueDescription("The codacy api base url.")
                            codacyApiBaseUrl: Option[String] = Some("https://api.codacy.com"))

final case class ExtraOptions(
  @Hidden @ValueDescription(s"The analyser to use. (${Analyser.allAnalysers.map(_.name).mkString(", ")})")
  analyser: String = Analyser.defaultAnalyser.name)

final case class Analyse(
  @Recurse
  options: CommonOptions,
  @Recurse
  api: APIOptions,
  //TODO: Consider if we want to list the tools, and how we want to show them
  @ExtraName("t") @ValueDescription(s"The tool to analyse the code.")
  tool: Option[String],
  @ExtraName("d") @ValueDescription("The directory to analyse.")
  directory: Option[File],
  @ExtraName("f") @ValueDescription(s"The output format. (${Formatter.allFormatters.map(_.name).mkString(", ")})")
  format: String = Formatter.defaultFormatter.name,
  @ExtraName("o") @ValueDescription("The output destination file.")
  output: Option[File] = Option.empty,
  @ExtraName("c") @ValueDescription("The commit UUID of the commit that will be analysed")
  commitUuid: Option[Commit.Uuid] = Option.empty,
  @ExtraName("u") @ValueDescription("If the results should be uploaded to the API")
  upload: Int @@ Counter = Tag.of(0),
  @ExtraName("p") @ValueDescription("The number of tools to run in parallel")
  parallel: Option[Int] = Option.empty,
  @ValueDescription("Allow tools to access the network")
  allowNetwork: Int @@ Counter = Tag.of(0),
  @ValueDescription("The maximum number of issues allowed for the analysis to succeed")
  maxAllowedIssues: Int = 0,
  @ValueDescription("Fail the analysis if any tool fails to run")
  failIfIncomplete: Int @@ Counter = Tag.of(0),
  @ValueDescription("Force files to be readable by changing the permissions before running the analysis")
  forceFilePermissions: Int @@ Counter = Tag.of(0),
  @ValueDescription("Maximum time each tool has to execute")
  toolTimeout: Option[Duration],
  @Hidden @ValueDescription("Fetch the tools and descriptions from Codacy public API")
  fetchRemoteTools: Boolean = false,
  @Recurse
  extras: ExtraOptions)
    extends CommandOptions {
  val uploadValue: Boolean = upload.## > 0
  val failIfIncompleteValue: Boolean = failIfIncomplete.## > 0
  val allowNetworkValue: Boolean = allowNetwork.## > 0
  val forceFilePermissionsValue: Boolean = forceFilePermissions.## > 0
}

final case class ValidateConfiguration(@Recurse
                                       options: CommonOptions,
                                       @ExtraName("d") @ValueDescription(
                                         "The directory where the configuration file is located")
                                       directory: Option[File] = Option.empty)
    extends CommandOptions
