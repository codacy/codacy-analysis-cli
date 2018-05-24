package com.codacy.analysis.cli.analysis

import com.codacy.analysis.cli.command.analyse.AnalyseExecutor.ExecutorResult

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
    val resultsCount = executorResultsEither
      .getOrElse(Seq.empty[ExecutorResult])
      .map { executorResult =>
        executorResult.analysisResults.map(_.size).getOrElse(0)
      }
      .sum

    if (executorResultsEither.isLeft) {
      ExitStatus.ExitCodes.failedAnalysis
    } else if (failIfIncomplete && executorResultsEither.exists(_.exists(_.analysisResults.isFailure))) {
      ExitStatus.ExitCodes.partiallyFailedAnalysis
    } else if (resultsCount > maxAllowedIssues) {
      ExitStatus.ExitCodes.maxAllowedIssuesExceeded
    } else if (uploadResult.isLeft) {
      ExitStatus.ExitCodes.failedUpload
    } else {
      ExitStatus.ExitCodes.success
    }
  }

}
