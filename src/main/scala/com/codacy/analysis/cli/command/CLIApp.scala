package com.codacy.analysis.cli.command

import better.files.File
import caseapp._
import caseapp.core.ArgParser
import com.codacy.analysis.cli.clients.api.ProjectConfiguration
import com.codacy.analysis.cli.clients.{CodacyClient, CodacyPlugins}
import com.codacy.analysis.cli.command.ArgumentParsers._
import com.codacy.analysis.cli.command.analyse.Analyser
import com.codacy.analysis.cli.converters.ConfigurationHelper
import com.codacy.analysis.cli.files.FileCollector
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.cli.model.{CodacyCfg, Configuration, FileCfg}
import com.codacy.analysis.cli.rules.AnalyseRules
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
                         @ExtraName("p") @ValueDescription("The project token.")
                         projectToken: Option[String],
                         @ValueDescription("The api token.")
                         apiToken: Option[String],
                         @ExtraName("u") @ValueDescription("The username.")
                         username: Option[String],
                         @ExtraName("p") @ValueDescription("The project name.")
                         project: Option[String],
                         @ExtraName("a") @ValueDescription("The codacy api base url.")
                         codacyApiBaseUrl: Option[String],
                         @ExtraName("t") @ValueDescription("The tool to analyse the code.")
                         tool: String,
                         @ExtraName("d") @ValueDescription("The directory to analyse.")
                         directory: File = Properties.codacyCode.getOrElse(File.currentWorkingDirectory),
                         @ExtraName("f") @ValueDescription(
                           s"The output format. (${Formatter.allFormatters.map(_.name).mkString(", ")})")
                         format: String = Formatter.defaultFormatter.name,
                         @ExtraName("o") @ValueDescription("The output destination file.")
                         output: Option[File] = Option.empty,
                         @Hidden @ExtraName("a") @ValueDescription(
                           s"The analyser to use. (${Analyser.allAnalysers.map(_.name).mkString(", ")})")
                         analyser: String = Analyser.defaultAnalyser.name)
    extends Command {

  // TODO: check if verbose is working
  private implicit val logger: Logger = utils.Logger.withLevel(getLogger, options.verbose.isDefined)
  private val formatterImpl: Formatter = Formatter(format, output)
  private val analyserImpl: Analyser[Try] = Analyser(analyser)
  private val fileCollector: FileCollector[Try] = FileCollector.defaultCollector.apply()
  private lazy val analyseRules = new AnalyseRules(sys.env)

  def run(): Unit = {
    formatterImpl.begin()

    // TODO: Move this to the file processor
    val baseDirectory = if (directory.isDirectory) directory else directory.parent
    val filesToAnalyse = if (directory.isDirectory) directory.listRecursively.to[Set] else Set(directory)

    val result = for {
      fileTarget <- fileCollector.list(tool, directory, null, toolConfiguration)
      results <- analyserImpl.analyse(tool, fileTarget.directory, fileTarget.files, FileCfg)
    } yield results

    result match {
      case Success(res) =>
        logger.info(s"Completed analysis for $tool")
        res.foreach(formatterImpl.add)
      case Failure(e) =>
        logger.error(e)(s"Failed analysis for $tool")
    }

    formatterImpl.end()
  }

  private def toolConfiguration: Configuration = {
    getToolConfiguration match {
      case Left(e) =>
        logger.warn(e)
        FileCfg
      case Right(conf) => conf
    }
  }

  private def getToolConfiguration: Either[String, Configuration] = {
    for {
      apiURL <- analyseRules.validateApiBaseUrl(codacyApiBaseUrl)
      projToken <- analyseRules.validateProjectToken(projectToken)
      projectConfig <- getRemoteProjectConfiguration(apiURL, projToken)
    } yield {
      projectConfig.toolConfiguration.collectFirst {
        case tc if CodacyPlugins.getPluginUuidByShortName(tool).contains(tc.uuid) =>
          CodacyCfg(tc.patterns.map(ConfigurationHelper.apiPatternToInternalPattern))
      }.getOrElse(FileCfg)
    }
  }

  private def getRemoteProjectConfiguration(apiURL: String, projToken: String): Either[String, ProjectConfiguration] = {
    //TODO: this should be in a different place...
    val codacyClient = new CodacyClient(Some(apiURL), Some(projToken))
    codacyClient.getProjectConfiguration
  }

}
