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
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.size mustEqual 1
          toolSet.head.name mustEqual expectedToolName
      }
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

      val expectedToolUuid1 = "34225275-f79e-4b85-8126-c7512c987c0d"
      val expectedToolUuid2 = "cf05f3aa-fd23-4586-8cce-5368917ec3e5"

      val userInput = None
      val remoteProjectConfiguration = Right(
        ProjectConfiguration(
          Set.empty,
          Set.empty,
          Set(
            ToolConfiguration(expectedToolUuid1, isEnabled = true, notEdited = false, Set.empty),
            ToolConfiguration(expectedToolUuid2, isEnabled = true, notEdited = false, Set.empty),
            ToolConfiguration("someRandomTool", isEnabled = false, notEdited = false, Set.empty),
            ToolConfiguration("anotherRandomTool", isEnabled = false, notEdited = false, Set.empty))))

      val toolEither = Tool.fromInput(userInput, remoteProjectConfiguration)
      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.size mustEqual 2
          toolSet.map(_.uuid) mustEqual Set(expectedToolUuid1, expectedToolUuid2)
      }
    }
  }

}
