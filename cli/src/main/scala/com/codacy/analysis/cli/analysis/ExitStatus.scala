package com.codacy.analysis.cli.analysis

import com.codacy.analysis.cli.CLIErrorMessage
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor.{
  DuplicationToolExecutorResult,
  ExecutorResult,
  IssuesToolExecutorResult,
  MetricsToolExecutorResult
}

object ExitStatus {

  object ExitCodes {
    val success = 0
    val failedAnalysis = 1
    val partiallyFailedAnalysis = 2
    val nonExistentTool = 301
    val uncommitedChanges = 302
    val failedUpload = 101
    val maxAllowedIssuesExceeded = 201
  }
}

class ExitStatus(maxAllowedIssues: Int, failIfIncomplete: Boolean = false) {

  def exitCode(resultsEither: Either[CLIErrorMessage, Seq[ExecutorResult]]): Int = {
    val resultsCount = countResults(resultsEither)

    resultsEither match {
      case Left(_: CLIErrorMessage.UncommitedChanges) =>
        ExitStatus.ExitCodes.uncommitedChanges
      case Left(_: CLIErrorMessage.NonExistingToolInput) =>
        ExitStatus.ExitCodes.nonExistentTool
      case Left(_: CLIErrorMessage.UploadError | _: CLIErrorMessage.MissingUploadRequisites) =>
        ExitStatus.ExitCodes.failedUpload
      case Left(_) =>
        ExitStatus.ExitCodes.failedAnalysis
      case Right(results) if failIfIncomplete && existsFailure(results) =>
        ExitStatus.ExitCodes.partiallyFailedAnalysis
      case Right(_) if resultsCount > maxAllowedIssues =>
        ExitStatus.ExitCodes.maxAllowedIssuesExceeded
      case _ =>
        ExitStatus.ExitCodes.success
    }
  }

  private def countResults(executorResultsEither: Either[CLIErrorMessage, Seq[ExecutorResult]]): Int = {
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
