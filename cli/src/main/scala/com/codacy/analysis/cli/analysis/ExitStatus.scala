package com.codacy.analysis.cli.analysis

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
    val failedUpload = 101
    val maxAllowedIssuesExceeded = 201
  }
}

class ExitStatus(maxAllowedIssues: Int, failIfIncomplete: Boolean = false) {

  def exitCode(executorResultsEither: Either[String, Seq[ExecutorResult]], uploadResult: Either[String, Unit]): Int = {
    val resultsCount = countResults(executorResultsEither)

    if (executorResultsEither.isLeft) {
      ExitStatus.ExitCodes.failedAnalysis
    } else if (failIfIncomplete && existsFailure(executorResultsEither)) {
      ExitStatus.ExitCodes.partiallyFailedAnalysis
    } else if (resultsCount > maxAllowedIssues) {
      ExitStatus.ExitCodes.maxAllowedIssuesExceeded
    } else if (uploadResult.isLeft) {
      ExitStatus.ExitCodes.failedUpload
    } else {
      ExitStatus.ExitCodes.success
    }
  }

  private def countResults(executorResultsEither: Either[String, Seq[ExecutorResult]]): Int = {
    executorResultsEither
      .getOrElse(Seq.empty[ExecutorResult])
      .map {
        case executorResult: IssuesToolExecutorResult =>
          executorResult.analysisResults.map(_.size).getOrElse(0)
        case executorResult: MetricsToolExecutorResult =>
          executorResult.analysisResults.map(_.size).getOrElse(0)
        case _: DuplicationToolExecutorResult =>
          0
      }
      .sum
  }

  private def existsFailure(executorResultsEither: Either[String, Seq[ExecutorResult]]): Boolean = {
    executorResultsEither.exists(_.exists {
      case executorResult: IssuesToolExecutorResult =>
        executorResult.analysisResults.isFailure
      case executorResult: MetricsToolExecutorResult =>
        executorResult.analysisResults.isFailure
      case _: DuplicationToolExecutorResult =>
        false
    })
  }

}
