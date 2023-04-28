package com.codacy.analysis.core.upload

import java.nio.file.Path

import cats.implicits._
import com.codacy.analysis.core.clients.CodacyClient
import com.codacy.analysis.core.git.Commit
import com.codacy.analysis.core.model.IssuesAnalysis.FileResults
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.utils.EitherOps
import com.codacy.analysis.core.configuration.AppConfiguration
import org.log4s.{Logger, getLogger}
import shapeless.syntax.std.TupleOps

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ResultsUploader {

  final case class ToolResults(tool: String, files: Set[Path], results: Either[String, Set[ToolResult]])

  val defaultBatchSize = AppConfiguration.batchSize

  private val logger: Logger = getLogger

  def apply(codacyClientOpt: Option[CodacyClient], upload: Boolean, commitUuidOpt: Option[Commit.Uuid], batchSize: Int)(
    implicit context: ExecutionContext): Either[String, Option[ResultsUploader]] = {
    if (upload) {
      for {
        codacyClient <- codacyClientOpt.toRight("No credentials found.")
        commitUuid <- commitUuidOpt.toRight("No commit found.")
      } yield Option(new ResultsUploader(commitUuid, codacyClient, batchSize))
    } else {
      logger.info(s"Upload step disabled")
      Option.empty[ResultsUploader].asRight[String]
    }
  }
}

class ResultsUploader private (commitUuid: Commit.Uuid, codacyClient: CodacyClient, uploadBatchSize: Int)(implicit
  context: ExecutionContext) {

  private val logger: Logger = getLogger

  private val batchSize: Int = uploadBatchSize match {
    case size if size > 0 => size
    case size =>
      logger.warn(s"Illegal value for upload batch size ($size), using default batch size")
      ResultsUploader.defaultBatchSize
  }

  def sendResults(toolResults: Seq[ResultsUploader.ToolResults],
                  metricsResults: Seq[MetricsResult],
                  duplicationResults: Seq[DuplicationResult]): Future[Either[String, Unit]] = {

    val sendIssuesFut = if (toolResults.nonEmpty) {
      sendIssues(toolResults)
    } else {
      logger.info("There are no issues to upload.")
      Future.successful(().asRight[String])
    }
    val sendMetricsFut = if (metricsResults.nonEmpty) {
      codacyClient.sendRemoteMetrics(commitUuid, metricsResults)
    } else {
      logger.info("There are no metrics to upload.")
      Future.successful(().asRight[String])
    }
    val sendDuplicationFut = if (duplicationResults.nonEmpty) {
      codacyClient.sendRemoteDuplication(commitUuid, duplicationResults)
    } else {
      logger.info("There is no duplication to upload.")
      Future.successful(().asRight[String])
    }

    val res: Future[Either[String, Unit]] =
      (sendIssuesFut, sendMetricsFut, sendDuplicationFut).mapN {
        case eithers =>
          EitherOps.sequenceFoldingLeft(new TupleOps(eithers).toList)(_ + '\n' + _)
      }.flatMap { _ =>
        endUpload()
      }

    res.onComplete {
      case Success(_) =>
        logger.info("Completed upload of results to API successfully")
      case Failure(e) =>
        logger.info(e)(s"Failed to push results to API")
    }

    res
  }

  private def sendIssues(toolResults: Seq[ResultsUploader.ToolResults]): Future[Either[String, Unit]] = {
    val uploadResultsBatches = toolResults.map { toolResult =>
      val fileResults =
        toolResult.results.map(results => groupResultsByFile(toolResult.files, results))
      uploadResultsBatch(toolResult.tool, batchSize, fileResults)
    }

    sequenceUploads(uploadResultsBatches)
  }

  private def endUpload(): Future[Either[String, Unit]] = {
    codacyClient.sendEndOfResults(commitUuid)
  }

  private def uploadResultsBatch(tool: String,
                                 batchSize: Int,
                                 results: Either[String, Set[FileResults]]): Future[Either[String, Unit]] = {
    val fileResultBatches = results.map(res => splitInBatches(batchSize, res))
    uploadResultBatches(tool, fileResultBatches)
  }

  private def uploadResultBatches(
    tool: String,
    fileResultBatches: Either[String, Seq[Set[FileResults]]]): Future[Either[String, Unit]] = {

    val responses: Seq[Future[Either[String, Unit]]] = fileResultBatches.fold(
      error => Seq(codacyClient.sendRemoteIssues(tool, commitUuid, Left(error))),
      _.map(fileResultBatch => codacyClient.sendRemoteIssues(tool, commitUuid, Right(fileResultBatch))))

    sequenceUploads(responses)
  }

  private def splitInBatches(batchSize: Int, results: Set[FileResults]): List[Set[FileResults]] = {

    def exceedsBatch(accumulatedFileResults: Set[FileResults], fileResults: FileResults): Boolean = {
      (accumulatedFileResults.map(_.results.size).sum + fileResults.results.size) >= batchSize
    }

    val (remainingFileResults, fileResultBatches) =
      results.foldLeft((Set.empty[FileResults], List.empty[Set[FileResults]])) {
        case ((accumulatedFileResults, resultBatches), fileResults)
            if exceedsBatch(accumulatedFileResults, fileResults) =>
          (Set(fileResults), resultBatches :+ accumulatedFileResults)

        case ((accumulatedFileResults, resultBatches), fileResults) =>
          (accumulatedFileResults + fileResults, resultBatches)
      }
    fileResultBatches :+ remainingFileResults
  }

  private def groupResultsByFile(files: Set[Path], results: Set[ToolResult]): Set[FileResults] = {

    val resultsByFile: Map[Path, Set[ToolResult]] = results.groupBy {
      case i: Issue      => i.filename
      case fe: FileError => fe.filename
    }

    files.map(filename =>
      FileResults(filename = filename, results = resultsByFile.getOrElse(filename, Set.empty[ToolResult])))
  }

  private def sequenceUploads(uploads: Seq[Future[Either[String, Unit]]]): Future[Either[String, Unit]] = {
    Future
      .sequence(uploads)
      .map(_.foldLeft[Either[String, Unit]](Right(())) {
        case (result, either) =>
          Seq(result, either).collect {
            case Left(error) => error
          }.reduceOption(_ + "\n" + _).map(Left.apply).getOrElse(Right(()))
      })
  }

}
