package com.codacy.analysis.cli

import better.files.File
import com.codacy.analysis.cli.analysis.ToolSelector
import com.codacy.analysis.cli.configuration.CLIConfiguration
import com.codacy.analysis.core.files.FilesTarget
import com.codacy.analysis.core.model.{AnalyserError, DuplicationToolSpec, MetricsToolSpec, PatternSpec, ToolSpec}
import com.codacy.analysis.core.tools.{Tool, ToolRepositoryStub}
import com.codacy.analysis.core.utils.LanguagesHelper
import com.codacy.plugins.api.languages.Languages.{Javascript, Python}
import com.codacy.plugins.api.languages.{Language, Languages}
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

class ToolSelectorSpec extends Specification with NoLanguageFeatures {

  val emptyFilesTarget = FilesTarget(File(""), Set.empty, Set.empty)
  val noLocalConfiguration = Left("no config")

  val toolRepository = new ToolRepositoryStub {

    private def getToolSpec(uuid: String, name: String, languages: Set[Language]) =
      ToolSpec(
        uuid = uuid,
        dockerImage = "codacy/codacy-example-tool:1.0.0",
        isDefault = true,
        version = "",
        languages = languages,
        name = name,
        shortName = name,
        documentationUrl = None,
        sourceCodeUrl = None,
        prefix = "",
        needsCompilation = false,
        hasConfigFile = true,
        configFilenames = Set.empty,
        standalone = false,
        hasUIConfiguration = true)

    override val allTools = Right(
      Seq(
        getToolSpec("34225275-f79e-4b85-8126-c7512c987c0d", "PyLint", Set(Python)),
        getToolSpec("c6273c22-5248-11e5-885d-feff819cdc9f", "Brakeman", Set(Languages.Ruby)),
        getToolSpec("724f98da-f616-4e37-9606-f16919137a1e", "Rubocop", Set(Languages.Ruby)),
        getToolSpec("38794ba2-94d8-4178-ab99-1f5c1d12760c", "BundlerAudit", Set(Languages.Ruby)),
        getToolSpec("cf05f3aa-fd23-4586-8cce-5368917ec3e5", "ESLint", Set(Languages.Javascript))))

    override def listPatterns(tool: ToolSpec): Either[AnalyserError, Seq[PatternSpec]] = Right(Seq.empty)

    override def listDuplicationTools(): Either[AnalyserError, Seq[DuplicationToolSpec]] =
      Right(Seq(DuplicationToolSpec("", Set(Languages.Ruby))))

    override def listMetricsTools(): Either[AnalyserError, Seq[MetricsToolSpec]] =
      Right(Seq(MetricsToolSpec("", Set(Languages.Ruby))))
  }
  val toolSelector: ToolSelector = new ToolSelector(toolRepository)

  "AnalyseExecutor.allTools" should {
    "find ruby tools" in {

      val toolConfiguration =
        CLIConfiguration.Tool(Option.empty, allowNetwork = false, Left("no config"), Option.empty, Map.empty)
      val rubyTools = toolSelector.allTools(None, toolConfiguration, Set(Languages.Ruby), "")

      rubyTools should beRight
      rubyTools must beLike {
        case Right(tools) =>
          tools.map(_.name) must containTheSameElementsAs(
            Seq("Brakeman", "Rubocop", "BundlerAudit", "metrics", "duplication"))
          tools.flatMap(_.supportedLanguages) must containAllOf(Seq(Languages.Ruby))
      }
    }
  }

  "AnalyseExecutor.tools" should {
    "fail on incorrect input (even if remote configuration is valid)" in {

      val expectedToolName = "SomeInvalidTool"

      val languages = LanguagesHelper.fromFileTarget(emptyFilesTarget, Map.empty)

      val toolEither = toolSelector.tool(expectedToolName, languages, "")
      toolEither must beLeft(CLIError.NonExistingToolInput(expectedToolName))
    }

    "fallback to remote configuration" in {

      val expectedToolUuid1 = "34225275-f79e-4b85-8126-c7512c987c0d"
      val expectedToolUuid2 = "cf05f3aa-fd23-4586-8cce-5368917ec3e5"

      val toolConfigs =
        Set(
          CLIConfiguration.IssuesTool(expectedToolUuid1, enabled = true, notEdited = false, Set.empty),
          CLIConfiguration.IssuesTool(expectedToolUuid2, enabled = true, notEdited = false, Set.empty),
          CLIConfiguration.IssuesTool("someRandomTool", enabled = false, notEdited = false, Set.empty),
          CLIConfiguration.IssuesTool("anotherRandomTool", enabled = false, notEdited = false, Set.empty))
      val toolConfiguration =
        CLIConfiguration.Tool(Option.empty, allowNetwork = false, Right(toolConfigs), Option.empty, Map.empty)

      val toolEither = toolSelector.tools(toolConfiguration, Set(Javascript, Python), "")
      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.size mustEqual 2
          toolSet.collect { case tool: Tool => tool.uuid } mustEqual Set(expectedToolUuid1, expectedToolUuid2)
      }
    }

    "fallback to finding tools if remote configuration is not present" in {
      val toolConfigs = Left("some error")
      val filesTarget = FilesTarget(File(""), Set(File("SomeClazz.rb").path), Set.empty)
      val toolConfiguration =
        CLIConfiguration.Tool(Option.empty, allowNetwork = false, toolConfigs, Option.empty, Map.empty)
      val languages = LanguagesHelper.fromFileTarget(filesTarget, Map.empty)

      val toolEither = toolSelector.tools(toolConfiguration, languages, "")
      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.map(_.name) mustEqual Set("Brakeman", "Rubocop", "BundlerAudit")
      }
    }

    "fallback to finding tools (with custom extensions) if remote configuration is not present" in {
      val toolConfigs = Left("some error")
      val filesTarget = FilesTarget(File(""), Set(File("SomeClazz.rawr").path), Set.empty)
      val languageExtensions: Map[Language, Set[String]] = Map(Languages.Ruby -> Set("rawr"))
      val toolConfiguration =
        CLIConfiguration.Tool(Option.empty, allowNetwork = true, toolConfigs, Option.empty, languageExtensions)
      val languages = LanguagesHelper.fromFileTarget(filesTarget, languageExtensions)

      val toolEither = toolSelector.tools(toolConfiguration, languages, "")
      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.map(_.name) mustEqual Set("Brakeman", "Rubocop", "BundlerAudit")
      }
    }
  }
}
