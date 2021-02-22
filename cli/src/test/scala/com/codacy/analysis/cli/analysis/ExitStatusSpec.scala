package com.codacy.analysis.cli.analysis

import java.nio.file.Paths

import com.codacy.analysis.cli.CLIError
import com.codacy.analysis.cli.analysis.AnalyseExecutor.IssuesToolExecutorResult
import com.codacy.analysis.core.git.Commit
import com.codacy.analysis.core.model.{FullLocation, Issue}
import com.codacy.plugins.api.results.{Pattern, Result}
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.util.{Failure, Success}

class ExitStatusSpec extends Specification with NoLanguageFeatures with Mockito with FutureMatchers {

  "ExitStatus" should {

    "send failed analysis code" in {
      new ExitStatus(3).exitCode(Left(CLIError.FilesAccessError)) should beEqualTo(ExitStatus.ExitCodes.failedAnalysis)
    }

    "send success code when issues do not exceed max issues number" in {
      new ExitStatus(10).exitCode(Right(Seq(IssuesToolExecutorResult(
        toolName = "MyTool",
        toolSpecification = None,
        patternDescriptions = Set.empty,
        prefix = None,
        files = Set(Paths.get("Test.scala")),
        // scalafmt: { binPack.defnSite = true }
        analysisResults = Success(Set(
          Issue(
            Pattern.Id("NoMutableVariables"),
            Paths.get("Test.scala"),
            Issue.Message("Mutable variable"),
            Result.Level.Err,
            Option(Pattern.Category.ErrorProne),
            FullLocation(1, 20)),
          Issue(
            Pattern.Id("NoMutableVariables"),
            Paths.get("Test.scala"),
            Issue.Message("Mutable variable"),
            Result.Level.Err,
            Option(Pattern.Category.ErrorProne),
            FullLocation(2, 20)),
          Issue(
            Pattern.Id("NoMutableVariables"),
            Paths.get("Test.scala"),
            Issue.Message("Mutable variable"),
            Result.Level.Err,
            Option(Pattern.Category.ErrorProne),
            FullLocation(3, 20)))))))) should beEqualTo(ExitStatus.ExitCodes.success)
    }

    "send exceed max issues number code when issues do not exceed max issues number" in {
      new ExitStatus(2).exitCode(Right(Seq(IssuesToolExecutorResult(
        toolName = "MyTool",
        toolSpecification = None,
        patternDescriptions = Set.empty,
        prefix = None,
        files = Set(Paths.get("Test.scala")),
        // scalafmt: { binPack.defnSite = true }
        analysisResults = Success(Set(
          Issue(
            Pattern.Id("NoMutableVariables"),
            Paths.get("Test.scala"),
            Issue.Message("Mutable variable"),
            Result.Level.Err,
            Option(Pattern.Category.ErrorProne),
            FullLocation(1, 20)),
          Issue(
            Pattern.Id("NoMutableVariables"),
            Paths.get("Test.scala"),
            Issue.Message("Mutable variable"),
            Result.Level.Err,
            Option(Pattern.Category.ErrorProne),
            FullLocation(2, 20)),
          Issue(
            Pattern.Id("NoMutableVariables"),
            Paths.get("Test.scala"),
            Issue.Message("Mutable variable"),
            Result.Level.Err,
            Option(Pattern.Category.ErrorProne),
            FullLocation(3, 20)))))))) should beEqualTo(ExitStatus.ExitCodes.maxAllowedIssuesExceeded)
    }

    "send success code when no issues" in {
      new ExitStatus(10).exitCode(
        Right(
          Seq(
            IssuesToolExecutorResult(
              toolName = "MyTool",
              toolSpecification = None,
              patternDescriptions = Set.empty,
              prefix = None,
              files = Set(Paths.get("Test.scala")),
              analysisResults = Success(Set()))))) should beEqualTo(ExitStatus.ExitCodes.success)
    }

    "send partial failure when some tools fail" in {
      new ExitStatus(10, failIfIncomplete = true).exitCode(Right(Seq(
        IssuesToolExecutorResult(
          toolName = "MyTool",
          toolSpecification = None,
          patternDescriptions = Set.empty,
          prefix = None,
          files = Set(),
          analysisResults = Success(Set())),
        IssuesToolExecutorResult(
          toolName = "MyTool",
          toolSpecification = None,
          patternDescriptions = Set.empty,
          prefix = None,
          files = Set(Paths.get("Test.scala")),
          analysisResults = Failure(new Exception("Failed")))))) should beEqualTo(
        ExitStatus.ExitCodes.partiallyFailedAnalysis)
    }

    "send ok when some tools fail and incomplete not requested" in {
      new ExitStatus(10, failIfIncomplete = false).exitCode(Right(Seq(
        IssuesToolExecutorResult(
          toolName = "MyTool",
          toolSpecification = None,
          patternDescriptions = Set.empty,
          prefix = None,
          files = Set(),
          analysisResults = Success(Set())),
        IssuesToolExecutorResult(
          toolName = "MyTool",
          toolSpecification = None,
          patternDescriptions = Set.empty,
          prefix = None,
          files = Set(Paths.get("Test.scala")),
          analysisResults = Failure(new Exception("Failed")))))) should beEqualTo(ExitStatus.ExitCodes.success)
    }

    "send failedUpload when uploader has an error" in {
      new ExitStatus(10, failIfIncomplete = true)
        .exitCode(Left(CLIError.UploadError("Failed upload!"))) should beEqualTo(ExitStatus.ExitCodes.failedUpload)
    }

    "send nonExistentTool when analysis fails because of non-existent tool argument value" in {
      new ExitStatus(10, failIfIncomplete = true)
        .exitCode(Left(CLIError.NonExistingToolInput("tool"))) should beEqualTo(ExitStatus.ExitCodes.nonExistentTool)
    }

    "send uncommitted changes exit code when a validation error of that type is present" in {
      new ExitStatus(10).exitCode(Left(CLIError.UncommitedChanges(Set.empty))) should beEqualTo(
        ExitStatus.ExitCodes.uncommittedChanges)
    }

    "send commit uuids do not match exit code when the commit uuid from git did not match the cli argument" in {
      new ExitStatus(10)
        .exitCode(Left(CLIError.CommitUuidsDoNotMatch(Commit.Uuid("arg"), Commit.Uuid("git")))) should beEqualTo(
        ExitStatus.ExitCodes.commitsDoNotMatch)
    }
  }

}
