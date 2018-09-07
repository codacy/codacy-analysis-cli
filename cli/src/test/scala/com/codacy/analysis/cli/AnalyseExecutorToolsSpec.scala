package com.codacy.analysis.cli

import better.files.File
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor
import com.codacy.analysis.core.clients.api.{ProjectConfiguration, ToolConfiguration}
import com.codacy.analysis.core.configuration.{CodacyConfigurationFile, LanguageConfiguration}
import com.codacy.analysis.core.files.FilesTarget
import com.codacy.analysis.core.tools.Tool
import com.codacy.analysis.core.utils.LanguagesHelper
import com.codacy.plugins.api.languages.Languages
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

class AnalyseExecutorToolsSpec extends Specification with NoLanguageFeatures {

  val emptyFilesTarget = FilesTarget(File(""), Set.empty, Set.empty)
  val noLocalConfiguration = Left("no config")

  "AnalyseExecutor.allTools" should {
    "find python tools" in {
      val pythonTools = AnalyseExecutor.allTools(None, Left("no remote config"), Set(Languages.Ruby), false)

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

      val userInput = Some(expectedToolName)
      val remoteProjectConfiguration = Right(
        ProjectConfiguration(
          Set.empty,
          Some(Set.empty),
          Set.empty,
          Set(ToolConfiguration("InvalidToolName", isEnabled = true, notEdited = false, Set.empty))))

      val languages = LanguagesHelper.fromFileTarget(emptyFilesTarget, noLocalConfiguration)

      val toolEither =
        AnalyseExecutor.tools(userInput, remoteProjectConfiguration, allowNetwork = false, languages)
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
          Some(Set.empty),
          Set.empty,
          Set(
            ToolConfiguration("34225275-f79e-4b85-8126-c7512c987c0d", isEnabled = true, notEdited = false, Set.empty))))

      val languages = LanguagesHelper.fromFileTarget(emptyFilesTarget, noLocalConfiguration)

      val toolEither =
        AnalyseExecutor.tools(userInput, remoteProjectConfiguration, allowNetwork = false, languages)
      toolEither must beLeft(CLIError.NonExistingToolInput(expectedToolName))
    }

    "fallback to remote configuration" in {

      val expectedToolUuid1 = "34225275-f79e-4b85-8126-c7512c987c0d"
      val expectedToolUuid2 = "cf05f3aa-fd23-4586-8cce-5368917ec3e5"

      val userInput = None
      val remoteProjectConfiguration = Right(
        ProjectConfiguration(
          Set.empty,
          Some(Set.empty),
          Set.empty,
          Set(
            ToolConfiguration(expectedToolUuid1, isEnabled = true, notEdited = false, Set.empty),
            ToolConfiguration(expectedToolUuid2, isEnabled = true, notEdited = false, Set.empty),
            ToolConfiguration("someRandomTool", isEnabled = false, notEdited = false, Set.empty),
            ToolConfiguration("anotherRandomTool", isEnabled = false, notEdited = false, Set.empty))))

      val languages = LanguagesHelper.fromFileTarget(emptyFilesTarget, noLocalConfiguration)

      val toolEither =
        AnalyseExecutor.tools(userInput, remoteProjectConfiguration, allowNetwork = false, languages)
      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.size mustEqual 2
          toolSet.collect { case tool: Tool => tool.uuid } mustEqual Set(expectedToolUuid1, expectedToolUuid2)
      }
    }

    "fallback to finding tools if remote configuration is not present" in {
      val userInput = None
      val remoteProjectConfiguration = Left("some error")

      val filesTarget = FilesTarget(File(""), Set(File("SomeClazz.rb").path), Set.empty)

      val languages = LanguagesHelper.fromFileTarget(filesTarget, noLocalConfiguration)

      val toolEither = AnalyseExecutor.tools(userInput, remoteProjectConfiguration, allowNetwork = false, languages)
      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.map(_.name) mustEqual Set("brakeman", "rubocop", "bundleraudit")
      }
    }

    "fallback to finding tools (with custom extensions) if remote configuration is not present" in {
      val userInput = None
      val remoteProjectConfiguration = Left("some error")

      val filesTarget = FilesTarget(File(""), Set(File("SomeClazz.rawr").path), Set.empty)

      val localConfiguration = Right(
        CodacyConfigurationFile(
          Option.empty,
          Option.empty,
          Option(Map(Languages.Java -> LanguageConfiguration(Option(Set("rawr")))))))

      val languages = LanguagesHelper.fromFileTarget(filesTarget, localConfiguration)

      val toolEither = AnalyseExecutor.tools(userInput, remoteProjectConfiguration, allowNetwork = true, languages)
      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.map(_.name) mustEqual Set("checkstyle", "findbugs", "findbugssec", "pmd", "pmd-legacy")
      }
    }

    """return an informative error message if the user selects a tool that needs access
      |to the network but doesn't provide the needed argument""".stripMargin in {
      val toolName = "gendarme"
      val remoteProjectConfiguration = Left("some error")
      val filesTarget = FilesTarget(File(""), Set(File("Test.cs").path), Set.empty)
      val localConfiguration = Right(CodacyConfigurationFile(Option.empty, Option.empty, Option.empty))

      val languages = LanguagesHelper.fromFileTarget(filesTarget, localConfiguration)

      val toolEither =
        AnalyseExecutor.tools(Some(toolName), remoteProjectConfiguration, allowNetwork = false, languages)

      toolEither must beLeft(CLIError.ToolNeedsNetwork(toolName))
    }

    "list tools that need access to the network if this argument is provided" in {
      val remoteProjectConfiguration = Left("some error")
      val filesTarget = FilesTarget(File(""), Set(File("Test.cs").path, File("Test.java").path), Set.empty)
      val localConfiguration = Right(CodacyConfigurationFile(Option.empty, Option.empty, Option.empty))

      val languages = LanguagesHelper.fromFileTarget(filesTarget, localConfiguration)

      val toolEither =
        AnalyseExecutor.tools(None, remoteProjectConfiguration, allowNetwork = true, languages)

      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          Tool.internetToolShortNames must contain(toolSet.map(_.name))
      }
    }

    "not list tools that need access to the network if this argument is not provided" in {
      val remoteProjectConfiguration = Left("some error")
      val filesTarget = FilesTarget(File(""), Set(File("Test.cs").path, File("Test.java").path), Set.empty)
      val localConfiguration = Right(CodacyConfigurationFile(Option.empty, Option.empty, Option.empty))

      val languages = LanguagesHelper.fromFileTarget(filesTarget, localConfiguration)

      val toolEither =
        AnalyseExecutor.tools(None, remoteProjectConfiguration, allowNetwork = false, languages)

      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          Tool.internetToolShortNames must not contain toolSet.map(_.name)
      }
    }

  }
}
