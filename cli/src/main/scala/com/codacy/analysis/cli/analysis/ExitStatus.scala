package com.codacy.analysis.cli.analysis

import com.codacy.analysis.cli.CLIError
import com.codacy.analysis.cli.analysis.AnalyseExecutor.{
  DuplicationToolExecutorResult,
  ExecutorResult,
  IssuesToolExecutorResult,
  MetricsToolExecutorResult
}

object ExitStatus {

  case class ExitCode(value: Int) extends AnyVal

  object ExitCodes {
    val success = ExitCode(0)
    val genericError = ExitCode(1)
    val timeout = ExitCode(2)
    val failedUpload = ExitCode(10)
    val uncommittedChanges = ExitCode(11)
    val commitsDoNotMatch = ExitCode(12)
    val nonExistentTool = ExitCode(13)
    val invalidConfigurationFile = ExitCode(14)
    val failedAnalysis = ExitCode(100)
    val partiallyFailedAnalysis = ExitCode(101)
    val maxAllowedIssuesExceeded = ExitCode(102)
  }

}

class ExitStatus(maxAllowedIssues: Int, failIfIncomplete: Boolean = false) {

  def exitCode(resultsEither: Either[CLIError, Seq[ExecutorResult[_]]]): ExitStatus.ExitCode = {
    val resultsCount = countResults(resultsEither)

    resultsEither match {
      case Left(_: CLIError.CommitUuidsDoNotMatch) =>
        ExitStatus.ExitCodes.commitsDoNotMatch
      case Left(_: CLIError.UncommitedChanges) =>
        ExitStatus.ExitCodes.uncommittedChanges
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
