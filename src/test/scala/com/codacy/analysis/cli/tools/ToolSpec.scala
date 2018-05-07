package com.codacy.analysis.cli.tools

import com.codacy.analysis.cli.clients.api.{ProjectConfiguration, ToolConfiguration}
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

class ToolSpec extends Specification with NoLanguageFeatures {

  "Tool" should {
    "use input over remote configuration" in {

      val expectedToolName = "pylint"

      val userInput = Some(expectedToolName)
      val remoteProjectConfiguration = Right(
        ProjectConfiguration(
          Set.empty,
          Set.empty,
          Set(ToolConfiguration("InvalidToolName", isEnabled = true, notEdited = false, Set.empty))))

      val toolEither = Tool.fromInput(userInput, remoteProjectConfiguration)
      toolEither must beRight
      toolEither must beLike { case Right(tool) => tool.name mustEqual expectedToolName }
    }

    "fail on incorrect input (even if remote configuration is valid)" in {

      val expectedToolName = "SomeInvalidTool"

      val userInput = Some(expectedToolName)
      val remoteProjectConfiguration = Right(
        ProjectConfiguration(
          Set.empty,
          Set.empty,
          Set(
            ToolConfiguration("34225275-f79e-4b85-8126-c7512c987c0d", isEnabled = true, notEdited = false, Set.empty))))

      val toolEither = Tool.fromInput(userInput, remoteProjectConfiguration)
      toolEither must beLeft
    }

    "failback to remote configuration" in {

      val expectedToolUuid = "34225275-f79e-4b85-8126-c7512c987c0d"

      val userInput = None
      val remoteProjectConfiguration = Right(
        ProjectConfiguration(
          Set.empty,
          Set.empty,
          Set(ToolConfiguration(expectedToolUuid, isEnabled = true, notEdited = false, Set.empty))))

      val toolEither = Tool.fromInput(userInput, remoteProjectConfiguration)
      toolEither must beRight
      toolEither must beLike { case Right(tool) => tool.uuid mustEqual expectedToolUuid }
    }
  }

}
