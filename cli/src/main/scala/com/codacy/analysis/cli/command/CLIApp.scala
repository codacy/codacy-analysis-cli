package com.codacy.analysis.cli.command

import better.files.File
import caseapp._
import caseapp.core.Error
import caseapp.core.argparser.{ArgParser, SimpleArgParser}
import caseapp.core.help.{Help, WithHelp}
import caseapp.core.parser.Parser
import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.command.ArgumentParsers._
import com.codacy.analysis.cli.command.Options._
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.core.clients.{OrganizationProvider, ProjectName, UserName}
import com.codacy.analysis.core.configuration.AppConfiguration
import com.codacy.analysis.core.git.Commit

import scala.concurrent.duration.Duration
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

/** Parses the `analyze` command in isolation. Reuses the `Parser`/`Help` instances declared
  * alongside `Analyze` in `Options` — see the note there for why.
  */
private[command] object AnalyzeParsing {

  def parse(args: List[String]): Either[Error, Analyze] =
    Options.analyzeParser.detailedParse(args).map(_._1)

  def parseAndRun(args: List[String],
                  run: Analyze => ExitStatus.ExitCode,
                  terminate: ExitStatus.ExitCode => Unit): Unit =
    Options.analyzeParser.withHelp.detailedParse(args) match {
      case Left(err) =>
        Console.err.println(err.message)
        terminate(ExitStatus.ExitCode(1))
      case Right((WithHelp(true, _, _), _)) =>
        Console.println(Options.analyzeHelp.usage)
        terminate(ExitStatus.ExitCodes.success)
      case Right((WithHelp(_, true, _), _)) =>
        Console.println(Options.analyzeHelp.help)
        terminate(ExitStatus.ExitCodes.success)
      case Right((WithHelp(_, _, Left(err)), _)) =>
        Console.err.println(err.message)
        terminate(ExitStatus.ExitCode(1))
      case Right((WithHelp(_, _, Right(options)), _)) =>
        terminate(run(options))
    }
}

/** Parses the `validate-configuration` command in isolation. Reuses the `Parser`/`Help`
  * instances declared alongside `ValidateConfiguration` in `Options` — see the note there.
  */
private[command] object ValidateConfigurationParsing {

  def parse(args: List[String]): Either[Error, ValidateConfiguration] =
    Options.validateConfigurationParser.detailedParse(args).map(_._1)

  def parseAndRun(args: List[String],
                  run: ValidateConfiguration => ExitStatus.ExitCode,
                  terminate: ExitStatus.ExitCode => Unit): Unit =
    Options.validateConfigurationParser.withHelp.detailedParse(args) match {
      case Left(err) =>
        Console.err.println(err.message)
        terminate(ExitStatus.ExitCode(1))
      case Right((WithHelp(true, _, _), _)) =>
        Console.println(Options.validateConfigurationHelp.usage)
        terminate(ExitStatus.ExitCodes.success)
      case Right((WithHelp(_, true, _), _)) =>
        Console.println(Options.validateConfigurationHelp.help)
        terminate(ExitStatus.ExitCodes.success)
      case Right((WithHelp(_, _, Left(err)), _)) =>
        Console.err.println(err.message)
        terminate(ExitStatus.ExitCode(1))
      case Right((WithHelp(_, _, Right(options)), _)) =>
        terminate(run(options))
    }
}

abstract class CLIApp {

  def progName: String = "codacy-analysis-cli"

  def run(commandOptions: CommandOptions): ExitStatus.ExitCode

  /** Overridable exit hook: tests override this to avoid tearing down the JVM. */
  def terminate(code: ExitStatus.ExitCode): Unit = sys.exit(code.value)

  def main(args: Array[String]): Unit =
    args.toList match {
      case Nil =>
        Console.println(usageMessage)
        terminate(ExitStatus.ExitCodes.success)
      case ("-v" | "--version") :: _ =>
        Console.println(s"codacy-analysis-cli is on version ${Version.version}")
        terminate(ExitStatus.ExitCodes.success)
      case "analyze" :: rest                => AnalyzeParsing.parseAndRun(rest, run, terminate)
      case "validate-configuration" :: rest => ValidateConfigurationParsing.parseAndRun(rest, run, terminate)
      case unknown :: _ =>
        Console.err.println(Error.CommandNotFound(unknown).message)
        terminate(ExitStatus.ExitCode(1))
    }

  private def usageMessage: String =
    s"Usage: $progName [--version] (analyze|validate-configuration) [options]"

  /** Parses `args` without running the resulting command. Used by tests to validate CLI parsing
    * in isolation.
    */
  def parseCommand(args: Array[String]): Either[Error, Unit] =
    args.toList match {
      case Nil                              => Right(())
      case ("-v" | "--version") :: _        => Right(())
      case "analyze" :: rest                => AnalyzeParsing.parse(rest).map(_ => ())
      case "validate-configuration" :: rest => ValidateConfigurationParsing.parse(rest).map(_ => ())
      case unknown :: _                     => Left(Error.CommandNotFound(unknown))
    }

}

object ArgumentParsers {

  private val commitUuidRegex: Regex = "^[a-fA-F0-9]{40}$".r

  implicit val fileParser: ArgParser[File] = {
    SimpleArgParser.from[File]("file") { path: String =>
      Right(File(path))
    }
  }

  implicit val providerParser: ArgParser[OrganizationProvider.Value] = {
    SimpleArgParser.from[OrganizationProvider.Value]("provider") { provider: String =>
      Try(OrganizationProvider.withName(provider)).toEither.left.map(e => Error.Other(e.toString))
    }
  }

  implicit val userNameParser: ArgParser[UserName] = {
    SimpleArgParser.from[UserName]("username") { username: String =>
      Right(UserName(username))
    }
  }

  implicit val projectNameParser: ArgParser[ProjectName] = {
    SimpleArgParser.from[ProjectName]("project") { project: String =>
      Right(ProjectName(project))
    }
  }

  implicit val durationParser: ArgParser[Duration] = {
    SimpleArgParser.from[Duration]("duration") { duration: String =>
      Try(Duration(duration)) match {
        case Success(d) => Right(d)
        case Failure(_) => Left(Error.Other(s"Invalid duration $duration (e.g. 20minutes, 10seconds, ...)"))
      }
    }
  }

  implicit val commitUuidParser: ArgParser[Commit.Uuid] = {
    SimpleArgParser.from[Commit.Uuid]("commitUuid") { commitUuid: String =>
      commitUuidRegex.findFirstIn(commitUuid) match {
        case Some(uuid) => Right(Commit.Uuid(uuid))
        case None       => Left(Error.Other(s"Invalid commit uuid $commitUuid - it must be a valid SHA hash"))
      }
    }
  }
}

object Version {

  val version: String =
    Option(getClass.getPackage.getImplementationVersion).getOrElse("0.1.0-SNAPSHOT")
}

// Note: case-app's Parser/Help derivation macro crashes scalac 2.13 in two distinct ways here,
// both reproduced in isolation and unrelated to case class size/shape/field count:
//  1. "key not found: package command" (LambdaLift) when the annotated case class is a bare
//     top-level member of a package — fixed by nesting the option case classes in this object,
//     which gives the macro-synthesized trees a class/object owner instead of the raw package.
//  2. "assertion failed: static" (delambdafy) when `Parser[X]`/`Help[X]` are derived from a
//     *different* object than the one `X` is declared in (e.g. calling `Parser[Analyze]` from
//     `AnalyzeParsing` while `Analyze` lives in `Options`) — fixed by declaring the derived
//     `Parser`/`Help` vals here, right next to the case class, and having the parsing objects
//     below reuse those vals instead of deriving their own.
object Options {

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
                              @ValueDescription("The provider.")
                              provider: Option[OrganizationProvider.Value] = Option.empty,
                              @ValueDescription("The username.")
                              username: Option[UserName] = Option.empty,
                              @ValueDescription("The project name.")
                              project: Option[ProjectName] = Option.empty,
                              @ValueDescription("The codacy api base url.")
                              codacyApiBaseUrl: Option[String] = Option.empty)

  final case class AdvancedOptions(
    @ExtraName("p") @ValueDescription("The number of tools to run in parallel")
    parallel: Option[Int] = Option.empty,
    @ValueDescription("Allow tools to access the network")
    allowNetwork: Int @@ Counter = Tag.of(0),
    @ValueDescription("The maximum number of issues allowed for the analysis to succeed")
    maxAllowedIssues: Int = 0,
    @ValueDescription("Fail the analysis if any tool fails to run")
    failIfIncomplete: Int @@ Counter = Tag.of(0),
    @ExtraName("r") @ValueDescription("[default: empty] - Alternative registry address (e.g. artprod.mycompany/)")
    registryAddress: String = "",
    @ValueDescription("Force files to be readable by changing the permissions before running the analysis")
    forceFilePermissions: Int @@ Counter = Tag.of(0),
    @ValueDescription("Maximum time each tool has to execute")
    toolTimeout: Option[Duration] = Option.empty,
    @ValueDescription("The tmp directory location.")
    tmpDirectory: Option[File] = None,
    @ValueDescription("The memory limit to run the tools with.")
    // Use the codacy-plugins default when not set.
    maxToolMemory: Option[String] = com.codacy.plugins.runners.BinaryDockerRunner.Config().containerMemoryLimit,
    @ValueDescription(
      "Reduce issue severity by one level, for non-security issues, for compatibility with GitHub's code scanning. Use in conjunction with `--format sarif`")
    ghCodeScanningCompat: Int @@ Counter = Tag.of(0),
    @Hidden // left for backward compatibility
    analyser: String = "")

  final case class Analyze(
    @Recurse
    options: CommonOptions,
    @Recurse
    api: APIOptions,
    @Recurse
    advanced: AdvancedOptions,
    @ExtraName("t") @ValueDescription("The tool to analyze the code.")
    tool: Option[String],
    @ExtraName("d") @ValueDescription("The directory to analyze.")
    directory: Option[File],
    @ExtraName("f") @ValueDescription(s"The output format. (${Formatter.allFormatters.map(_.name).mkString(", ")})")
    format: String = Formatter.defaultFormatter.name,
    @ExtraName("o") @ValueDescription("The output destination file.")
    output: Option[File] = Option.empty,
    @ExtraName("c") @ValueDescription("The commit UUID of the commit that will be analysed")
    commitUuid: Option[Commit.Uuid] = Option.empty,
    @ExtraName("s") @ValueDescription("[default: false] - Force using a commit UUID")
    skipCommitUuidValidation: Int @@ Counter = Tag.of(0),
    @ExtraName("g") @ValueDescription(
      "[default: false] - Skip the check for uncommitted files in the analysis directory")
    skipUncommittedFilesCheck: Int @@ Counter = Tag.of(0),
    @ExtraName("u") @ValueDescription("If the results should be uploaded to the API")
    upload: Int @@ Counter = Tag.of(0),
    @ValueDescription(s"Batch size for upload of results. If not set defaults to ${AppConfiguration.batchSize}")
    uploadBatchSize: Int = AppConfiguration.batchSize,
    @ExtraName("i") @ValueDescription(
      "[default: false] - Skip the SSL certificate verification when communicating with the Codacy API")
    skipSslVerification: Int @@ Counter = Tag.of(0))
      extends CommandOptions {
    def parallel: Option[Int] = advanced.parallel
    def maxAllowedIssues: Int = advanced.maxAllowedIssues
    def registryAddress: String = advanced.registryAddress
    def toolTimeout: Option[Duration] = advanced.toolTimeout
    def tmpDirectory: Option[File] = advanced.tmpDirectory
    def maxToolMemory: Option[String] = advanced.maxToolMemory

    val uploadValue: Boolean = upload.## > 0
    val failIfIncompleteValue: Boolean = advanced.failIfIncomplete.## > 0
    val allowNetworkValue: Boolean = advanced.allowNetwork.## > 0
    val forceFilePermissionsValue: Boolean = advanced.forceFilePermissions.## > 0
    val ghCodeScanningCompatValue: Boolean = advanced.ghCodeScanningCompat.## > 0
    val skipCommitUuidValidationValue: Boolean = skipCommitUuidValidation.## > 0
    val skipUncommittedFilesCheckValue: Boolean = skipUncommittedFilesCheck.## > 0
    val skipSslVerificationValue: Boolean = skipSslVerification.## > 0

  }
  val analyzeParser: Parser[Analyze] = Parser[Analyze]
  val analyzeHelp: Help[Analyze] = Help[Analyze]

  final case class ValidateConfiguration(@Recurse
                                         options: CommonOptions,
                                         @ExtraName("d") @ValueDescription(
                                           "The directory where the configuration file is located")
                                         directory: Option[File] = Option.empty)
      extends CommandOptions
  val validateConfigurationParser: Parser[ValidateConfiguration] = Parser[ValidateConfiguration]
  val validateConfigurationHelp: Help[ValidateConfiguration] = Help[ValidateConfiguration]

}
