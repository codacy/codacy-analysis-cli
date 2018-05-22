package com.codacy.analysis.cli.analysis

import com.codacy.analysis.cli.command.analyse.AnalyseExecutor.ExecutorResult

class Status(maxAllowedIssues: Int) {

  object ExitCodes {
    val success = 0
    val failedAnalysis = 1
    val maxAllowedIssuesExceeded = 101
  }

  def exitCode(executorResultsEither: Either[String, Seq[ExecutorResult]]): Int = {
    val resultsCount = executorResultsEither
      .getOrElse(Seq.empty[ExecutorResult])
      .map { executorResult =>
        executorResult.analysisResults.map(_.size).getOrElse(0)
      }
      .sum

    if (executorResultsEither.isLeft) {
      ExitCodes.failedAnalysis
    } else if (resultsCount > maxAllowedIssues) {
      ExitCodes.maxAllowedIssuesExceeded
    } else {
      ExitCodes.success
    }
  }

}
