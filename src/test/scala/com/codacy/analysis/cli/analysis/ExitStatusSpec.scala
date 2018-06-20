package com.codacy.analysis.cli.analysis

import java.nio.file.Paths

import com.codacy.analysis.cli.command.analyse.AnalyseExecutor.ExecutorResult
import com.codacy.analysis.cli.model.{FullLocation, Issue}
import com.codacy.plugins.api.results.{Pattern, Result}
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.util.{Failure, Success}

class ExitStatusSpec extends Specification with NoLanguageFeatures with Mockito with FutureMatchers {

  "ExitStatus" should {

    "send failed analysis code" in {
      new ExitStatus(3).exitCode(Left("Failed analysis"), Right(())) should beEqualTo(
        ExitStatus.ExitCodes.failedAnalysis)
    }

    "send success code when issues do not exceed max issues number" in {
      new ExitStatus(10).exitCode(
        Right(
          Seq(ExecutorResult(
            "MyTool",
            Set(Paths.get("Test.scala")),
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
        Right(())) should beEqualTo(ExitStatus.ExitCodes.success)
    }

    "send exceed max issues number code when issues do not exceed max issues number" in {
      new ExitStatus(2).exitCode(
        Right(
          Seq(ExecutorResult(
            "MyTool",
            Set(Paths.get("Test.scala")),
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
        Right(())) should beEqualTo(ExitStatus.ExitCodes.maxAllowedIssuesExceeded)
    }

    "send success code when no issues" in {
      new ExitStatus(10).exitCode(
        Right(Seq(ExecutorResult("MyTool", Set(Paths.get("Test.scala")), Success(Set())))),
        Right(())) should beEqualTo(ExitStatus.ExitCodes.success)
    }

    "send partial failure when some tools fail" in {
      new ExitStatus(10, failIfIncomplete = true).exitCode(
        Right(
          Seq(
            ExecutorResult("MyTool", Set(), Success(Set())),
            ExecutorResult("MyTool", Set(Paths.get("Test.scala")), Failure(new Exception("Failed"))))),
        Right(())) should beEqualTo(ExitStatus.ExitCodes.partiallyFailedAnalysis)
    }

    "send ok when some tools fail and incomplete not requested" in {
      new ExitStatus(10, failIfIncomplete = false).exitCode(
        Right(
          Seq(
            ExecutorResult("MyTool", Set(), Success(Set())),
            ExecutorResult("MyTool", Set(Paths.get("Test.scala")), Failure(new Exception("Failed"))))),
        Right(())) should beEqualTo(ExitStatus.ExitCodes.success)
    }

    "send failedUpload when uploader has an error" in {
      new ExitStatus(10, failIfIncomplete = true)
        .exitCode(Right(Seq()), Left("Failed to get uploader")) should beEqualTo(ExitStatus.ExitCodes.failedUpload)
    }
  }

}
