package com.codacy.analysis.cli

import better.files.File
import com.codacy.analysis.cli.analysis.AnalyseExecutor
import com.codacy.analysis.cli.configuration.CLIConfiguration
import com.codacy.analysis.core.clients.{CodacyTool, CodacyToolPattern, ToolsInformationRepository}
import com.codacy.analysis.core.files.{FileCollector, FilesTarget}
import com.codacy.analysis.core.tools.{Tool, ToolCollector}
import com.codacy.analysis.core.utils.LanguagesHelper
import com.codacy.plugins.api.languages.Languages.{Javascript, Python}
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.results.docker.java.checkstyle.Checkstyle
import com.codacy.plugins.results.docker.java.findbugs.SpotBugs
import com.codacy.plugins.results.docker.java.pmd.PMD
import com.codacy.plugins.results.docker.js.eslint.ESLint
import com.codacy.plugins.results.docker.python.pylint.PyLint
import com.codacy.plugins.results.docker.ruby.brakeman.Brakeman
import com.codacy.plugins.results.docker.ruby.bundlerAudit.BundlerAudit
import com.codacy.plugins.results.docker.ruby.rubocop.Rubocop
import com.codacy.plugins.results.traits.DockerTool
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

class AnalyseExecutorToolsSpec extends Specification with NoLanguageFeatures {

  val emptyFilesTarget = FilesTarget(File(""), Set.empty, Set.empty)
  val noLocalConfiguration = Left("no config")

  private def analyseExecutor = {
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

    val fileCollector: FileCollector[Try] = FileCollector.defaultCollector()

    val toolsInformationRepository = new ToolsInformationRepository {
      override def toolsList: Future[Either[String, Set[CodacyTool]]] = {

        val tools: Set[DockerTool] = Set(Brakeman, Rubocop, BundlerAudit, Checkstyle, PMD, PyLint, ESLint, SpotBugs)
        val codacyTools = tools.map { tool =>
          CodacyTool(
            tool.uuid,
            tool.name,
            "version",
            tool.shortName,
            Some(tool.documentationUrl),
            Some(tool.sourceCodeUrl),
            Some(tool.prefix),
            tool.needsCompilation,
            tool.configFilename,
            Some("description"),
            tool.dockerImageName,
            tool.languages.map(_.toString),
            tool.isClientSide,
            tool.isDefault,
            tool.hasUIConfiguration)
        }
        Future.successful(Right(codacyTools))
      }

      override def toolPatterns(toolUuid: String): Future[immutable.Seq[CodacyToolPattern]] =
        Future.successful(immutable.Seq.empty)
    }

    val toolCollector = new ToolCollector(toolsInformationRepository)
    new AnalyseExecutor(null, null, fileCollector, null, toolCollector)
  }

  "AnalyseExecutor.allTools" should {
    "find python tools" in {

      val toolConfiguration =
        CLIConfiguration.Tool(Option.empty, allowNetwork = false, Left("no config"), Option.empty, Map.empty)
      val pythonTools = Await.result(analyseExecutor.allTools(None, toolConfiguration, Set(Languages.Ruby)), 5.seconds)

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
        Await.result(analyseExecutor.tools(userInput, toolConfiguration, Set(Python)), 5.seconds)
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
        Await.result(analyseExecutor.tools(userInput, toolConfiguration, languages), 5.seconds)
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
        Await.result(analyseExecutor.tools(userInput, toolConfiguration, Set(Javascript, Python)), 5.seconds)
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

      val toolEither = Await.result(analyseExecutor.tools(userInput, toolConfiguration, languages), 5.seconds)
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

      val toolEither = Await.result(analyseExecutor.tools(userInput, toolConfiguration, languages), 5.seconds)
      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.map(_.name) mustEqual Set("spotbugs", "checkstyle", "pmd")
      }
    }
  }
}
