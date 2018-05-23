package com.codacy.analysis.cli.analysis

import java.nio.file.Paths

import codacy.docker.api.{Pattern, Result}
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor.ExecutorResult
import com.codacy.analysis.cli.model.{FullLocation, Issue}
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.util.{Failure, Success}

class StatusSpec extends Specification with NoLanguageFeatures with Mockito with FutureMatchers {

  "Status" should {

    "send failed analysis code" in {
      new Status(3).exitCode(Left("Failed analysis"), Right(())) should beEqualTo(Status.ExitCodes.failedAnalysis)
    }

    "send success code when issues do not exceed max issues number" in {
      new Status(10).exitCode(
        Right(
          Seq(ExecutorResult(
            "MyTool",
            // scalafmt: { binPack.defnSite = true }
            Success(Set(
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
                FullLocation(3, 20))))))),
        Right(())) should beEqualTo(Status.ExitCodes.success)
    }

    "send exceed max issues number code when issues do not exceed max issues number" in {
      new Status(2).exitCode(
        Right(
          Seq(ExecutorResult(
            "MyTool",
            // scalafmt: { binPack.defnSite = true }
            Success(Set(
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
                FullLocation(3, 20))))))),
        Right(())) should beEqualTo(Status.ExitCodes.maxAllowedIssuesExceeded)
    }

    "send success code when no issues" in {
      new Status(10).exitCode(Right(Seq(ExecutorResult("MyTool", Success(Set())))), Right(())) should beEqualTo(
        Status.ExitCodes.success)
    }

    "send partial failure when some tools fail" in {
      new Status(10, failIfIncomplete = true).exitCode(
        Right(
          Seq(ExecutorResult("MyTool", Success(Set())), ExecutorResult("MyTool", Failure(new Exception("Failed"))))),
        Right(())) should beEqualTo(Status.ExitCodes.partiallyFailedAnalysis)
    }

    "send ok when some tools fail and incomplete not requested" in {
      new Status(10, failIfIncomplete = false).exitCode(
        Right(
          Seq(ExecutorResult("MyTool", Success(Set())), ExecutorResult("MyTool", Failure(new Exception("Failed"))))),
        Right(())) should beEqualTo(Status.ExitCodes.success)
    }

    "send failedUpload when uploader has an error" in {
      new Status(10, failIfIncomplete = true).exitCode(Right(Seq()), Left("Failed to get uploader")) should beEqualTo(
        Status.ExitCodes.failedUpload)
    }
  }

}
