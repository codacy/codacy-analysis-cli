package com.codacy.analysis.cli.upload

import java.nio.file.Path

import cats.implicits._
import com.codacy.analysis.cli.clients.CodacyClient
import com.codacy.analysis.cli.model.{FileError, FileResults, Issue, Result}
import org.log4s.{Logger, getLogger}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object ResultsUploader {

  final case class ToolResults(tool: String, files: Set[Path], results: Set[Result])

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

  def sendResults(toolResults: Seq[ResultsUploader.ToolResults]): Future[Either[String, Unit]] = {
    val uploadResultsBatches = toolResults.map { toolResult =>
      val fileResults = groupResultsByFile(toolResult.files, toolResult.results)
      uploadResultsBatch(toolResult.tool, batchSize, fileResults)
    }

    val res = sequenceUploads(uploadResultsBatches).flatMap {
      case Right(_)        => endUpload()
      case error @ Left(_) => Future.successful(error)
    }

    res.onComplete {
      case Success(_) =>
        logger.info("Completed upload of results to API successfully")
      case Failure(e) =>
        logger.info(e)(s"Failed to push results to API")
    }

    res
  }

  private def endUpload(): Future[Either[String, Unit]] = {
    codacyClient.sendEndOfResults(commitUuid)
  }

  private def uploadResultsBatch(tool: String,
                                 batchSize: Int,
                                 results: Set[FileResults]): Future[Either[String, Unit]] = {
    val fileResultBatches = splitInBatches(batchSize, results)
    uploadResultBatches(tool, fileResultBatches)
  }

  private def uploadResultBatches(tool: String,
                                  fileResultBatches: Seq[Set[FileResults]]): Future[Either[String, Unit]] = {
    val responses = fileResultBatches.map { fileResultBatch =>
      codacyClient.sendRemoteResults(tool, commitUuid, fileResultBatch)
    }
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

  private def groupResultsByFile(files: Set[Path], results: Set[Result]): Set[FileResults] = {
    val resultsByFile: Map[Path, Set[Result]] = results.groupBy {
      case i: Issue      => i.filename
      case fe: FileError => fe.filename
    }

    files.map(filename => FileResults(filename, resultsByFile.getOrElse(filename, Set.empty[Result])))(
      collection.breakOut)
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
