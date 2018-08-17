package com.codacy.analysis.cli.analysis

import com.codacy.analysis.cli.command.analyse.AnalyseExecutor.{
  DuplicationToolExecutorResult,
  ExecutorResult,
  ExecutorErrorMessage,
  IssuesToolExecutorResult,
  MetricsToolExecutorResult
}
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor

object ExitStatus {

  object ExitCodes {
    val success = 0
    val failedAnalysis = 1
    val partiallyFailedAnalysis = 2
    val nonExistentTool = 3
    val failedUpload = 101
    val maxAllowedIssuesExceeded = 201
  }
}

class ExitStatus(maxAllowedIssues: Int, failIfIncomplete: Boolean = false) {

  def exitCode(executorResultsEither: Either[ExecutorErrorMessage, Seq[ExecutorResult]],
               uploadResult: Either[String, Unit]): Int = {
    val resultsCount = countResults(executorResultsEither)

    executorResultsEither match {
      case Left(_: AnalyseExecutor.NonExistingToolInput) =>
        ExitStatus.ExitCodes.nonExistentTool
      case Left(_) =>
        ExitStatus.ExitCodes.failedAnalysis
      case Right(results) if failIfIncomplete && existsFailure(results) =>
        ExitStatus.ExitCodes.partiallyFailedAnalysis
      case Right(_) if resultsCount > maxAllowedIssues =>
        ExitStatus.ExitCodes.maxAllowedIssuesExceeded
      case _ if uploadResult.isLeft =>
        ExitStatus.ExitCodes.failedUpload
      case _ =>
        ExitStatus.ExitCodes.success
    }
  }

  private def countResults(executorResultsEither: Either[ExecutorErrorMessage, Seq[ExecutorResult]]): Int = {
    executorResultsEither
      .getOrElse(Seq.empty[ExecutorResult])
      .map {
        case executorResult: IssuesToolExecutorResult =>
          executorResult.analysisResults.map(_.size).getOrElse(0)
        case _ =>
          0
      }
      .sum
  }

  private def existsFailure(executorResults: Seq[ExecutorResult]): Boolean = {
    executorResults.exists {
      case executorResult: IssuesToolExecutorResult =>
        executorResult.analysisResults.isFailure
      case executorResult: MetricsToolExecutorResult =>
        executorResult.analysisResults.isFailure
      case executorResult: DuplicationToolExecutorResult =>
        executorResult.analysisResults.isFailure
    }
  }

}
