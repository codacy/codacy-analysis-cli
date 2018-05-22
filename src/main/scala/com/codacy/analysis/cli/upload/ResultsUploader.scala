package com.codacy.analysis.cli.upload

import com.codacy.analysis.cli.clients.CodacyClient
import com.codacy.analysis.cli.model.Result
import org.log4s.{Logger, getLogger}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class ResultsUploader(commitUuid: String, codacyClient: CodacyClient, batchSizeOpt: Option[Int])(
  implicit context: ExecutionContext) {

  private val logger: Logger = getLogger

  //TODO: Make this a config
  val defaultBatchSize = 50000
  private val batchSize: Int = batchSizeOpt.map {
    case size if size > 0 => size
    case size =>
      logger.warn(s"Illegal value for upload batch size ($size), using default batch size")
      defaultBatchSize
  }.getOrElse(defaultBatchSize)

  def sendResults(tool: String, results: Set[Result]): Future[Either[String, Unit]] = {
    val res = uploadResultsBatch(tool, batchSize, results).flatMap {
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

  private def uploadResultsBatch(tool: String, batchSize: Int, results: Set[Result]): Future[Either[String, Unit]] = {
    if (results.size <= batchSize) {
      codacyClient.sendRemoteResults(tool, commitUuid, results)
    } else {
      val batch: Future[Either[String, Unit]] =
        codacyClient.sendRemoteResults(tool, commitUuid, results.take(batchSize))
      val remainingBatches: Future[Either[String, Unit]] =
        uploadResultsBatch(tool, batchSize, results.drop(batchSize))
      sequenceUploads(batch, remainingBatches)
    }
  }

  private def sequenceUploads(call1: Future[Either[String, Unit]], call2: Future[Either[String, Unit]]) = {
    for {
      res1 <- call1
      res2 <- call2
    } yield {
      Seq(res1, res2).collect {
        case Left(error) => error
      }.reduceOption(_ + "\n" + _).map(Left.apply).getOrElse(Right(()))
    }
  }

}
