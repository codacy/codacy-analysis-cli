package com.codacy.analysis.cli.analysis

import com.codacy.analysis.cli.CLIError
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor.{
  DuplicationToolExecutorResult,
  ExecutorResult,
  IssuesToolExecutorResult,
  MetricsToolExecutorResult
}

object ExitStatus {

  object ExitCodes {
    val success = 0
    val genericError = 1
    val timeout = 2
    val failedUpload = 10
    val uncommitedChanges = 11
    val commitsDoNotMatch = 12
    val nonExistentTool = 13
    val failedAnalysis = 100
    val partiallyFailedAnalysis = 101
    val maxAllowedIssuesExceeded = 102
  }

}

class ExitStatus(maxAllowedIssues: Int, failIfIncomplete: Boolean = false) {

  def exitCode(resultsEither: Either[CLIError, Seq[ExecutorResult[_]]]): Int = {
    val resultsCount = countResults(resultsEither)

    resultsEither match {
      case Left(_: CLIError.CommitUuidsDoNotMatch) =>
        ExitStatus.ExitCodes.commitsDoNotMatch
      case Left(_: CLIError.UncommitedChanges) =>
        ExitStatus.ExitCodes.uncommitedChanges
      case Left(_: CLIError.NonExistingToolInput) =>
        ExitStatus.ExitCodes.nonExistentTool
      case Left(_: CLIError.UploadError | _: CLIError.MissingUploadRequisites) =>
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

  private def countResults(executorResultsEither: Either[CLIError, Seq[ExecutorResult[_]]]): Int = {
    executorResultsEither
      .getOrElse(Seq.empty[ExecutorResult[_]])
      .map {
        case executorResult: IssuesToolExecutorResult =>
          executorResult.analysisResults.map(_.size).getOrElse(0)
        case _ =>
          0
      }
      .sum
  }

  private def existsFailure(executorResults: Seq[ExecutorResult[_]]): Boolean = {
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
