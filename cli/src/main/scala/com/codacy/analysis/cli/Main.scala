package com.codacy.analysis.cli

import java.nio.file.Path

import better.files._
import cats.implicits._
import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.clients.Credentials
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor._
import com.codacy.analysis.cli.command.{Analyse, CLIApp, Command}
import com.codacy.analysis.cli.configuration.{CLIConfiguration, Environment}
import com.codacy.analysis.cli.formatter.Formatter
import com.codacy.analysis.core.analysis.Analyser
import com.codacy.analysis.core.clients.CodacyClient
import com.codacy.analysis.core.configuration.CodacyConfigurationFile
import com.codacy.analysis.core.files.FileCollector
import com.codacy.analysis.core.git.{Commit, Git}
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.upload.ResultsUploader
import com.codacy.analysis.core.utils.IOHelper.IOThrowable
import com.codacy.analysis.core.utils.Logger
import com.codacy.analysis.core.utils.SeqOps._
import org.log4s.getLogger
import scalaz.zio.ExitResult.{Completed, Failed, Terminated}
import scalaz.zio.{IO, RTS}

import scala.util.{Failure, Success}

object Main extends MainImpl(sys.env)

class MainImpl(env: Map[String, String]) extends CLIApp with RTS {

  private val logger: org.log4s.Logger = getLogger

  override def run(command: Command): Unit = {
    exit(runCommand(command))
  }

  def runCommand(command: Command): Int = {
    command match {
      case analyse: Analyse =>
        Logger.setLevel(analyse.options.verboseValue)

        val environment = new Environment(env)
        val codacyClientOpt: Option[CodacyClient] = Credentials.get(environment, analyse.api).map(CodacyClient.apply)

        val configuration: IO[Nothing, CLIConfiguration] =
          CLIConfiguration(codacyClientOpt, environment, analyse, new CodacyConfigurationFile.Loader)

        val program: IO[(CLIConfiguration, CLIError), (CLIConfiguration, Seq[ExecutorResult[_]])] =
          configuration.flatMap { configuration =>
            removeCodacyRuntimeConfigurationFiles(configuration.analysis.projectDirectory)

            val analysisAndUpload = for {
              _ <- validate(analyse, configuration)
              analysisResults <- analysis(Analyser(analyse.extras.analyser), configuration.analysis)
              _ <- upload(configuration.upload, codacyClientOpt, analysisResults)
            } yield {
              analysisResults
            }

            removeCodacyRuntimeConfigurationFiles(configuration.analysis.projectDirectory)

            def zipWithConfig[T](t: T): (CLIConfiguration, T) = (configuration, t)

            analysisAndUpload.bimap(zipWithConfig, zipWithConfig)
          }

        runAndExit(program)
    }
  }

  private def runAndExit(program: IO[(CLIConfiguration, CLIError), (CLIConfiguration, Seq[ExecutorResult[_]])]): Int = {
    unsafeRunSync(program) match {
      case Completed((configuration, value)) =>
        new ExitStatus(configuration.result.maxAllowedIssues, configuration.result.failIfIncomplete)
          .exitCode(Right(value))
      case Failed((configuration, err), _) =>
        new ExitStatus(configuration.result.maxAllowedIssues, configuration.result.failIfIncomplete).exitCode(Left(err))
      case Terminated(_) =>
        -1
    }
  }

  private def validate(analyse: Analyse, configuration: CLIConfiguration): IO[CLIError, Unit] = {
    for {
      _ <- validateNoUncommitedChanges(configuration.analysis.projectDirectory, configuration.upload.upload)
      _ <- validateCommitUuid(configuration.analysis.projectDirectory, analyse.commitUuid)
    } yield ()
  }

  private def validateNoUncommitedChanges(projectDirectory: File, upload: Boolean): IO[CLIError, Unit] = {
    (for {
      repo <- Git.repository(projectDirectory)
      uncommitedFiles <- repo.uncommitedFiles
    } yield uncommitedFiles).redeem(_ => IO.point(()), { uncommitedFiles =>
      if (uncommitedFiles.nonEmpty && upload) {
        val error: CLIError = CLIError.UncommitedChanges(uncommitedFiles)
        logger.error(error.message)
        IO.fail(error)
      } else {
        IO.point(())
      }
    })
  }

  private def validateCommitUuid(projectDirectory: File, commitUuidOpt: Option[Commit.Uuid]): IO[CLIError, Unit] = {
    commitUuidOpt match {
      case None =>
        IO.point(())
      case Some(paramCommitUuid) =>
        (for {
          repo <- Git.repository(projectDirectory)
          gitCommit <- repo.latestCommit
        } yield gitCommit).redeem(_ => IO.point(()), { gitCommit =>
          if (gitCommit.commitUuid != paramCommitUuid) {
            val error = CLIError.CommitUuidsDoNotMatch(paramCommitUuid, gitCommit.commitUuid)
            logger.error(error.message)
            IO.fail(error)
          } else {
            IO.point(())
          }
        })
    }
  }

  private def analysis(analyser: Analyser[IOThrowable],
                       configuration: CLIConfiguration.Analysis): IO[CLIError, Seq[ExecutorResult[_]]] = {
    val formatter: Formatter = Formatter(configuration.output.format, configuration.output.file)
    val fileCollector: FileCollector[IOThrowable] = FileCollector.defaultCollector()

    new AnalyseExecutor(formatter, analyser, fileCollector, configuration).run()
  }

  private def upload(configuration: CLIConfiguration.Upload,
                     codacyClientOpt: Option[CodacyClient],
                     analysisResults: Seq[AnalyseExecutor.ExecutorResult[_]]): IO[CLIError, Unit] = {

    val uploadResultFut: IO[Nothing, Either[String, Unit]] =
      uploadResults(codacyClientOpt)(configuration.upload, configuration.commitUuid, analysisResults)

    if (configuration.upload) {
      uploadResultFut.flatMap {
        case Left(err) =>
          logger.warn(err)
          IO.fail(CLIError.MissingUploadRequisites(err))
        case Right(_) =>
          logger.info("Completed upload of results to API")
          IO.point(())
      }
    } else IO.point(())
  }

  private def uploadResults(codacyClientOpt: Option[CodacyClient])(
    upload: Boolean,
    commitUuid: Option[Commit.Uuid],
    executorResults: Seq[ExecutorResult[_]]): IO[Nothing, Either[String, Unit]] = {
    (for {
      uploaderOpt <- ResultsUploader(codacyClientOpt, upload, commitUuid)
    } yield {
      uploaderOpt.map { uploader =>
        val (issuesToolExecutorResult, metricsToolExecutorResult, duplicationToolExecutorResult) =
          executorResults
            .partitionSubtypes[IssuesToolExecutorResult, MetricsToolExecutorResult, DuplicationToolExecutorResult]

        val issuesPerToolSeq = issuesPerTool(issuesToolExecutorResult)
        val issuesResultsSeq = issuesResults(issuesPerToolSeq)

        val metricsPerLanguageSeq = metricsPerLanguage(metricsToolExecutorResult)
        val metricsResultsSeq = metricsResults(metricsPerLanguageSeq)

        val duplicationResultsSeq = duplicationResults(duplicationToolExecutorResult)

        uploader.sendResults(issuesResultsSeq, metricsResultsSeq, duplicationResultsSeq)
      }.getOrElse(IO.point(().asRight[String]))
    }).fold(err => IO.point(err.asLeft[Unit]), identity)
  }

  def duplicationResults(duplicationExecutorToolResults: Seq[DuplicationToolExecutorResult]): Seq[DuplicationResult] = {
    duplicationExecutorToolResults.map {
      case DuplicationToolExecutorResult(language, files, Success(duplicationClones)) =>
        DuplicationResult(language, DuplicationAnalysis.Success(files, duplicationClones))
      case DuplicationToolExecutorResult(language, _, Failure(err)) =>
        DuplicationResult(language, DuplicationAnalysis.Failure(err.getMessage))
    }
  }

  private def metricsResults(
    languageAndToolResultSeq: Seq[(String, (Set[Path], Either[Throwable, Set[FileMetrics]]))]): Seq[MetricsResult] = {
    languageAndToolResultSeq.groupBy {
      case (language, _) => language
    }.flatMap {
      case (language, languageAndFileMetricsSeq) =>
        languageAndFileMetricsSeq.map {
          case (_, (files, Right(fileMetrics))) =>
            MetricsResult(language, MetricsAnalysis.Success(fileWithMetrics(files, fileMetrics)))
          case (_, (_, Left(err))) =>
            MetricsResult(language, MetricsAnalysis.Failure(err.getMessage))
        }(collection.breakOut)
    }(collection.breakOut)
  }

  private def fileWithMetrics(allFiles: Set[Path], fileMetrics: Set[FileMetrics]): Set[MetricsAnalysis.FileResults] = {
    allFiles.map { file =>
      val metrics = fileMetrics.find(_.filename == file).map { metrics =>
        Metrics(
          metrics.complexity,
          metrics.loc,
          metrics.cloc,
          metrics.nrMethods,
          metrics.nrClasses,
          metrics.lineComplexities)
      }
      MetricsAnalysis.FileResults(file, metrics)
    }
  }

  private def metricsPerLanguage(metricsToolExecutorResult: Seq[MetricsToolExecutorResult])
    : Seq[(String, (Set[Path], Either[Throwable, Set[FileMetrics]]))] = {
    metricsToolExecutorResult.flatMap {
      case MetricsToolExecutorResult(language, files, Success(fileMetrics)) =>
        Option((language, (files, Right(fileMetrics))))
      case MetricsToolExecutorResult(language, files, Failure(err)) =>
        Option((language, (files, Left(err))))
    }
  }

  private def issuesResults(toolAndIssuesResults: Seq[(String, (Set[Path], Either[Throwable, Set[ToolResult]]))])
    : Seq[ResultsUploader.ToolResults] = {
    toolAndIssuesResults.groupBy {
      case (toolName, _) => toolName
    }.flatMap {
      case (toolName, toolAndIssuesSeq) =>
        toolAndIssuesSeq.map {
          case (_, (files, Right(issues))) =>
            ResultsUploader.ToolResults(toolName, files, Right(issues))
          case (_, (files, Left(err))) =>
            ResultsUploader.ToolResults(toolName, files, Left(err.getMessage))
        }(collection.breakOut)

    }(collection.breakOut)
  }

  private def issuesPerTool(issuesToolExecutorResult: Seq[IssuesToolExecutorResult])
    : Seq[(String, (Set[Path], Either[Throwable, Set[ToolResult]]))] = {
    issuesToolExecutorResult.flatMap {
      case IssuesToolExecutorResult(tool, files, Success(results)) =>
        Option((tool, (files, Right(results))))
      case IssuesToolExecutorResult(tool, files, Failure(err)) =>
        Option((tool, (files, Left(err))))
    }
  }

  //TODO: this can be removed when all tools are using the 3.+ seed version.
  private def removeCodacyRuntimeConfigurationFiles(directory: File): Unit = {
    directory / ".codacy.json" delete (swallowIOExceptions = true)
    directory / ".codacyrc" delete (swallowIOExceptions = true)
    ()
  }
}
