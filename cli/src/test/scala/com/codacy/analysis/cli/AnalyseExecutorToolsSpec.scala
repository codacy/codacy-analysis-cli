package com.codacy.analysis.cli

import better.files.File
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.cli.configuration.CLIConfiguration
import com.codacy.analysis.core.files.FilesTarget
import com.codacy.analysis.core.tools.Tool
import com.codacy.analysis.core.utils.LanguagesHelper
import com.codacy.plugins.api.languages.Languages.{Javascript, Python}
import com.codacy.plugins.api.languages.{Language, Languages}
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

class AnalyseExecutorToolsSpec extends Specification with NoLanguageFeatures {

  val emptyFilesTarget = FilesTarget(File(""), Set.empty, Set.empty)
  val noLocalConfiguration = Left("no config")

  "AnalyseExecutor.allTools" should {
    "find python tools" in {

      val toolConfiguration =
        CLIConfiguration.Tool(Option.empty, allowNetwork = false, Left("no config"), Option.empty, Map.empty)
      val pythonTools = AnalyseExecutor.allTools(None, toolConfiguration, Set(Languages.Ruby))

      pythonTools should beRight
      pythonTools must beLike {
        case Right(tools) =>
          tools.map(_.name) must containTheSameElementsAs(
            Seq("brakeman", "rubocop", "bundleraudit", "metrics", "duplication"))
          tools.flatMap(_.supportedLanguages) must containAllOf(Seq(Languages.Ruby))
      }
    }
  }

  "AnalyseExecutor.tools" should {
    "use input over remote configuration" in {

      val expectedToolName = "pylint"
      val toolConfigs =
        Set(CLIConfiguration.IssuesTool("InvalidToolName", enabled = true, notEdited = false, Set.empty))
      val toolConfiguration =
        CLIConfiguration.Tool(Option.empty, allowNetwork = false, Right(toolConfigs), Option.empty, Map.empty)
      val userInput = Some(expectedToolName)

      val toolEither =
        AnalyseExecutor.tools(userInput, toolConfiguration, Set(Python))
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
      val toolConfigs =
        Set(
          CLIConfiguration
            .IssuesTool("34225275-f79e-4b85-8126-c7512c987c0d", enabled = true, notEdited = false, Set.empty))
      val toolConfiguration =
        CLIConfiguration.Tool(Option.empty, allowNetwork = false, Right(toolConfigs), Option.empty, Map.empty)
      val languages = LanguagesHelper.fromFileTarget(emptyFilesTarget, Map.empty)

      val toolEither =
        AnalyseExecutor.tools(userInput, toolConfiguration, languages)
      toolEither must beLeft(CLIError.NonExistingToolInput(expectedToolName))
    }

    "fallback to remote configuration" in {

      val expectedToolUuid1 = "34225275-f79e-4b85-8126-c7512c987c0d"
      val expectedToolUuid2 = "cf05f3aa-fd23-4586-8cce-5368917ec3e5"

      val userInput = None
      val toolConfigs =
        Set(
          CLIConfiguration.IssuesTool(expectedToolUuid1, enabled = true, notEdited = false, Set.empty),
          CLIConfiguration.IssuesTool(expectedToolUuid2, enabled = true, notEdited = false, Set.empty),
          CLIConfiguration.IssuesTool("someRandomTool", enabled = false, notEdited = false, Set.empty),
          CLIConfiguration.IssuesTool("anotherRandomTool", enabled = false, notEdited = false, Set.empty))
      val toolConfiguration =
        CLIConfiguration.Tool(Option.empty, allowNetwork = false, Right(toolConfigs), Option.empty, Map.empty)

      val toolEither =
        AnalyseExecutor.tools(userInput, toolConfiguration, Set(Javascript, Python))
      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.size mustEqual 2
          toolSet.collect { case tool: Tool => tool.uuid } mustEqual Set(expectedToolUuid1, expectedToolUuid2)
      }
    }

    "fallback to finding tools if remote configuration is not present" in {
      val userInput = None
      val toolConfigs = Left("some error")
      val filesTarget = FilesTarget(File(""), Set(File("SomeClazz.rb").path), Set.empty)
      val toolConfiguration =
        CLIConfiguration.Tool(Option.empty, allowNetwork = false, toolConfigs, Option.empty, Map.empty)
      val languages = LanguagesHelper.fromFileTarget(filesTarget, Map.empty)

      val toolEither = AnalyseExecutor.tools(userInput, toolConfiguration, languages)
      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.map(_.name) mustEqual Set("brakeman", "rubocop", "bundleraudit")
      }
    }

    "fallback to finding tools (with custom extensions) if remote configuration is not present" in {
      val userInput = None
      val toolConfigs = Left("some error")
      val filesTarget = FilesTarget(File(""), Set(File("SomeClazz.rawr").path), Set.empty)
      val languageExtensions: Map[Language, Set[String]] = Map(Languages.Java -> Set("rawr"))
      val toolConfiguration =
        CLIConfiguration.Tool(Option.empty, allowNetwork = true, toolConfigs, Option.empty, languageExtensions)
      val languages = LanguagesHelper.fromFileTarget(filesTarget, languageExtensions)

      val toolEither = AnalyseExecutor.tools(userInput, toolConfiguration, languages)
      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.map(_.name) mustEqual Set("checkstyle", "findbugs", "findbugssec", "pmd", "pmd-legacy")
      }
    }

    """return an informative error message if the user selects a tool that needs access
      |to the network but doesn't provide the needed argument""".stripMargin in {
      val toolName = "gendarme"
      val toolConfigs = Left("some error")
      val filesTarget = FilesTarget(File(""), Set(File("Test.cs").path), Set.empty)
      val toolConfiguration =
        CLIConfiguration.Tool(Option.empty, allowNetwork = false, toolConfigs, Option.empty, Map.empty)
      val languages = LanguagesHelper.fromFileTarget(filesTarget, Map.empty)

      val toolEither =
        AnalyseExecutor.tools(Some(toolName), toolConfiguration, languages)

      toolEither must beLeft(CLIError.ToolNeedsNetwork(toolName))
    }

    "list tools that need access to the network if this argument is provided" in {
      val toolConfigs = Left("some error")
      val filesTarget = FilesTarget(File(""), Set(File("Test.cs").path, File("Test.java").path), Set.empty)
      val toolConfiguration =
        CLIConfiguration.Tool(Option.empty, allowNetwork = true, toolConfigs, Option.empty, Map.empty)
      val languages = LanguagesHelper.fromFileTarget(filesTarget, Map.empty)

      val toolEither =
        AnalyseExecutor.tools(None, toolConfiguration, languages)

      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          Tool.internetToolShortNames must contain(toolSet.map(_.name))
      }
    }

    "not list tools that need access to the network if this argument is not provided" in {
      val toolConfigs = Left("some error")
      val filesTarget = FilesTarget(File(""), Set(File("Test.cs").path, File("Test.java").path), Set.empty)
      val toolConfiguration =
        CLIConfiguration.Tool(Option.empty, allowNetwork = false, toolConfigs, Option.empty, Map.empty)
      val languages = LanguagesHelper.fromFileTarget(filesTarget, Map.empty)

      val toolEither =
        AnalyseExecutor.tools(None, toolConfiguration, languages)

      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          Tool.internetToolShortNames must not contain toolSet.map(_.name)
      }
    }

  }
}
