package com.codacy.analysis.core.upload

import java.nio.file.Path

import cats.implicits._
import com.codacy.analysis.core.clients.CodacyClient
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.utils.EitherOps
import org.log4s.{Logger, getLogger}
import shapeless.syntax.std.TupleOps

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ResultsUploader {

  final case class ToolResults(tool: String, files: Set[Path], results: Either[String, Set[ToolResult]])

  //TODO: Make this a config
  val defaultBatchSize = 50000

  private val logger: Logger = getLogger

  def apply(codacyClientOpt: Option[CodacyClient],
            upload: Boolean,
            commitUuidOpt: Option[String],
            batchSize: Option[Int] = Option.empty[Int])(
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

class ResultsUploader private (commitUuid: String, codacyClient: CodacyClient, batchSizeOpt: Option[Int])(
  implicit context: ExecutionContext) {

  private val logger: Logger = getLogger

  private val batchSize: Int = batchSizeOpt.map {
    case size if size > 0 => size
    case size =>
      logger.warn(s"Illegal value for upload batch size ($size), using default batch size")
      ResultsUploader.defaultBatchSize
  }.getOrElse(ResultsUploader.defaultBatchSize)

  def sendResults(toolResults: Seq[ResultsUploader.ToolResults],
                  metricsResults: Seq[MetricsResult]): Future[Either[String, Unit]] = {

    val sendIssuesFut = if (toolResults.nonEmpty) {
      sendIssues(toolResults)
    } else {
      logger.info("There are no issues to upload.")
      Future(().asRight[String])
    }
    val sendMetricsFut = if (metricsResults.nonEmpty) {
      codacyClient.sendRemoteMetrics(commitUuid, metricsResults)
    } else {
      logger.info("There are no metrics to upload.")
      Future(().asRight[String])
    }

    val res: Future[Either[String, Unit]] = (sendIssuesFut, sendMetricsFut).mapN {
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
      val fileResults = groupResultsByFile(toolResult.files, toolResult.results)
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
    val fileResultBatches = splitInBatches(batchSize, results)
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

  private def splitInBatches(
    batchSize: Int,
    resultsEither: Either[String, Set[FileResults]]): Either[String, List[Set[FileResults]]] = {

    def exceedsBatch(accumulatedFileResults: Set[FileResults], fileResults: FileResults): Boolean = {
      (accumulatedFileResults.map(_.results.size).sum + fileResults.results.size) >= batchSize
    }

    resultsEither.map { results =>
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
  }

  private def groupResultsByFile(files: Set[Path],
                                 results: Either[String, Set[ToolResult]]): Either[String, Set[FileResults]] = {

    val resultsByFileEither: Either[String, Map[Path, Set[ToolResult]]] = results.map(_.groupBy {
      case i: Issue      => i.filename
      case fe: FileError => fe.filename
    })

    resultsByFileEither.map(resultsByFile =>
      files.map(filename =>
        FileResults(filename = filename, results = resultsByFile.getOrElse(filename, Set.empty[ToolResult]))))
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
