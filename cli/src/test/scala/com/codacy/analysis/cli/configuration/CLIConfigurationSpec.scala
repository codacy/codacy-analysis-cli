package com.codacy.analysis.cli.configuration

import better.files.File
import caseapp.Tag
import com.codacy.analysis.cli.command.{APIOptions, Analyse, CommonOptions, ExtraOptions}
import com.codacy.analysis.cli.formatter.Json
import com.codacy.analysis.core.analysis.Analyser
import com.codacy.analysis.core.clients._
import com.codacy.analysis.core.clients.api._
import com.codacy.analysis.core.configuration.{CodacyConfigurationFile, EngineConfiguration, LanguageConfiguration}
import com.codacy.analysis.core.files.Glob
import com.codacy.analysis.core.git.Commit
import com.codacy.analysis.core.utils.HttpHelper
import com.codacy.plugins.api.languages.Languages
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification
import play.api.libs.json.JsString
import scalaz.zio.RTS

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class CLIConfigurationSpec extends Specification with NoLanguageFeatures with RTS {

  private val apiTokenStr = "RandomApiToken"
  private val username = "some_user"
  private val project = "some_project"
  private val remoteUrl = "codacy.com/2.0"
  private val apiCredentials: Credentials =
    APIToken(apiTokenStr, Option(remoteUrl), UserName(username), ProjectName(project))

  private object noLocalConfig extends CodacyConfigurationFile.Loader {
    override def load(directory: File) = Left("no local config")
  }
  private val toolInput = Option("hey! i'm a tool!")
  private val commitUuid = Option(Commit.Uuid("uuid"))
  private val defaultAnalyse = Analyse(
    options = CommonOptions(),
    api = APIOptions(),
    tool = toolInput,
    directory = Option.empty,
    format = Json.name,
    toolTimeout = Option.empty,
    commitUuid = commitUuid,
    extras = ExtraOptions(analyser = Analyser.defaultAnalyser.name))
  private val defaultEnvironment = new Environment(Map.empty)
  private val httpHelper = new HttpHelper(Option(remoteUrl), Map.empty)
  private val noRemoteConfigCodacyClient = new CodacyClient(apiCredentials, httpHelper)(ExecutionContext.global) {
    override def getRemoteConfiguration: Either[String, ProjectConfiguration] = {
      Left("no remote config")
    }
  }

  "CLIConfiguration" should {

    "parse configuration from analyse command" in {

      (for {
        directory <- File.temporaryDirectory()
        outputFile <- File.temporaryFile()
      } yield {
        val analyse = Analyse(
          options = CommonOptions(),
          api = APIOptions(),
          tool = toolInput,
          directory = Option(directory),
          output = Option(outputFile),
          format = Json.name,
          parallel = Option(2),
          commitUuid = commitUuid,
          toolTimeout = Option(20.seconds),
          forceFilePermissions = Tag.of(1),
          extras = ExtraOptions(analyser = Analyser.defaultAnalyser.name),
          allowNetwork = Tag.of(1),
          upload = Tag.of(1),
          maxAllowedIssues = 5,
          failIfIncomplete = Tag.of(1))

        val expectedConfiguration = CLIConfiguration(
          analysis = CLIConfiguration.Analysis(
            projectDirectory = directory,
            output = CLIConfiguration.Output(Json.name, Option(outputFile)),
            tool = toolInput,
            parallel = Option(2),
            forceFilePermissions = true,
            fileExclusionRules = CLIConfiguration.FileExclusionRules(
              defaultIgnores = Option.empty,
              ignoredPaths = Set.empty,
              excludePaths = CLIConfiguration.FileExclusionRules.ExcludePaths(Set.empty, Map.empty),
              allowedExtensionsByLanguage = Map.empty),
            toolConfiguration = CLIConfiguration.Tool(
              toolTimeout = Option(20.seconds),
              allowNetwork = true,
              toolConfigurations = Left("no remote config"),
              extraToolConfigurations = Option.empty,
              extensionsByLanguage = Map.empty)),
          upload = CLIConfiguration.Upload(commitUuid = commitUuid, upload = true),
          result = CLIConfiguration.Result(maxAllowedIssues = 5, failIfIncomplete = true))

        val actualConfiguration =
          CLIConfiguration(Option(noRemoteConfigCodacyClient), defaultEnvironment, analyse, noLocalConfig)

        unsafeRun(actualConfiguration) must beEqualTo(expectedConfiguration)
      }).get

    }

    "parse configuration with remote configuration only" in {

      val toolConfig = ToolConfiguration(
        uuid = "dat uuid fool!",
        isEnabled = true,
        notEdited = true,
        patterns = Set(
          ToolPattern(
            internalId = "dat internal id man!",
            parameters = Set(ToolParameter("dat param name", "dat param value!")))))
      val defaultIgnores = Set(PathRegex("dat regex bro!"))
      val ignoredPaths = Set(FilePath("dat file path yo!"))

      val codacyClient = new CodacyClient(apiCredentials, httpHelper)(ExecutionContext.global) {
        override def getRemoteConfiguration: Either[String, ProjectConfiguration] = {
          Right(
            ProjectConfiguration(
              ignoredPaths = ignoredPaths,
              defaultIgnores = Option(defaultIgnores),
              projectExtensions = Set(LanguageExtensions(Languages.Scala, Set(".scala", ".alacs"))),
              toolConfiguration = Set(toolConfig)))
        }
      }

      val expectedConfiguration = CLIConfiguration(
        CLIConfiguration.Analysis(
          projectDirectory = File.currentWorkingDirectory,
          output = CLIConfiguration.Output(Json.name, Option.empty),
          tool = toolInput,
          parallel = Option.empty,
          forceFilePermissions = false,
          fileExclusionRules = CLIConfiguration.FileExclusionRules(
            defaultIgnores = Option(defaultIgnores),
            ignoredPaths = ignoredPaths,
            excludePaths = CLIConfiguration.FileExclusionRules.ExcludePaths(global = Set.empty, byTool = Map.empty),
            allowedExtensionsByLanguage = Map(Languages.Scala -> Set(".scala", ".alacs"))),
          toolConfiguration = CLIConfiguration.Tool(
            toolTimeout = Option.empty,
            allowNetwork = false,
            toolConfigurations = Right(
              Set(CLIConfiguration.IssuesTool(
                uuid = toolConfig.uuid,
                enabled = toolConfig.isEnabled,
                notEdited = toolConfig.notEdited,
                patterns = toolConfig.patterns.map(pattern =>
                  CLIConfiguration.IssuesTool.Pattern(
                    id = pattern.internalId,
                    parameters = pattern.parameters.map(parameter =>
                      CLIConfiguration.IssuesTool.Parameter(name = parameter.name, value = parameter.value))))))),
            extraToolConfigurations = Option.empty,
            extensionsByLanguage = Map.empty)),
        upload = CLIConfiguration.Upload(commitUuid = commitUuid, upload = false),
        result = CLIConfiguration.Result(maxAllowedIssues = 0, failIfIncomplete = false))

      val actualConfiguration =
        CLIConfiguration(Option(codacyClient), defaultEnvironment, defaultAnalyse, noLocalConfig)

      unsafeRun(actualConfiguration) must beEqualTo(expectedConfiguration)

    }

    "parse configuration with local configuration only" in {

      val engine9Excludes = Set(Glob("this"), Glob("is a"), Glob("deftones track"))
      val engineConfig = EngineConfiguration(
        exclude_paths = Option(engine9Excludes),
        baseSubDir = Option("this/aint/no/m*****f****n/stick/up"),
        extraValues = Option(Map("just pick" -> JsString("the stick up!"))))
      val globalExcludes = Set(Glob("global exclude #1"), Glob("global exclude #2"))

      object localConfig extends CodacyConfigurationFile.Loader {
        override def load(directory: File): Either[String, CodacyConfigurationFile] = {
          Right(
            CodacyConfigurationFile(
              engines = Option(Map("engine no. 9" -> engineConfig)),
              exclude_paths = Option(globalExcludes),
              languages =
                Option(Map(Languages.Scala -> LanguageConfiguration(extensions = Option(Set(".scala", ".alacs")))))))
        }
      }

      val expectedConfiguration = CLIConfiguration(
        CLIConfiguration.Analysis(
          projectDirectory = File.currentWorkingDirectory,
          output = CLIConfiguration.Output(Json.name, Option.empty),
          tool = toolInput,
          parallel = Option.empty,
          forceFilePermissions = false,
          fileExclusionRules = CLIConfiguration.FileExclusionRules(
            defaultIgnores = Option.empty,
            ignoredPaths = Set.empty,
            excludePaths = CLIConfiguration.FileExclusionRules
              .ExcludePaths(global = globalExcludes, byTool = Map("engine no. 9" -> engine9Excludes)),
            allowedExtensionsByLanguage = Map(Languages.Scala -> Set(".scala", ".alacs"))),
          toolConfiguration = CLIConfiguration.Tool(
            toolTimeout = Option.empty,
            allowNetwork = false,
            toolConfigurations = Left("no remote config"),
            extraToolConfigurations = Option(
              Map("engine no. 9" -> CLIConfiguration.IssuesTool
                .Extra(baseSubDir = engineConfig.baseSubDir, extraValues = engineConfig.extraValues))),
            extensionsByLanguage = Map(Languages.Scala -> Set(".scala", ".alacs")))),
        upload = CLIConfiguration.Upload(commitUuid = commitUuid, upload = false),
        result = CLIConfiguration.Result(maxAllowedIssues = 0, failIfIncomplete = false))

      val actualConfiguration =
        CLIConfiguration(Option(noRemoteConfigCodacyClient), defaultEnvironment, defaultAnalyse, localConfig)

      unsafeRun(actualConfiguration) must beEqualTo(expectedConfiguration)
    }

    "parse configuration with environment and analyse command values (no remote or local configs)" in {

      val environment = new Environment(Map("CODACY_CODE" -> "."))

      val expectedConfiguration = CLIConfiguration(
        CLIConfiguration.Analysis(
          projectDirectory = File("."),
          output = CLIConfiguration.Output(Json.name, Option.empty),
          tool = toolInput,
          parallel = Option.empty,
          forceFilePermissions = false,
          fileExclusionRules = CLIConfiguration.FileExclusionRules(
            defaultIgnores = Option.empty,
            ignoredPaths = Set.empty,
            excludePaths = CLIConfiguration.FileExclusionRules.ExcludePaths(global = Set.empty, byTool = Map.empty),
            allowedExtensionsByLanguage = Map.empty),
          toolConfiguration = CLIConfiguration.Tool(
            toolTimeout = Option.empty,
            allowNetwork = false,
            toolConfigurations = Left("no remote config"),
            extraToolConfigurations = Option.empty,
            extensionsByLanguage = Map.empty)),
        upload = CLIConfiguration.Upload(commitUuid = commitUuid, upload = false),
        result = CLIConfiguration.Result(maxAllowedIssues = 0, failIfIncomplete = false))

      val actualConfiguration =
        CLIConfiguration(Option(noRemoteConfigCodacyClient), environment, defaultAnalyse, noLocalConfig)
      unsafeRun(actualConfiguration) must beEqualTo(expectedConfiguration)
    }

    "parse configuration with both local and remote configurations" in {

      val engine9Excludes = Set(Glob("this"), Glob("is a"), Glob("deftones track"))
      val engineConfig = EngineConfiguration(
        exclude_paths = Option(engine9Excludes),
        baseSubDir = Option("this/aint/no/m*****f****n/stick/up"),
        extraValues = Option(Map("just pick" -> JsString("the stick up!"))))
      val globalExcludes = Set(Glob("global exclude #1"), Glob("global exclude #2"))

      object localConfig extends CodacyConfigurationFile.Loader {
        override def load(directory: File): Either[String, CodacyConfigurationFile] = {
          Right(
            CodacyConfigurationFile(
              engines = Option(Map("engine no. 9" -> engineConfig)),
              exclude_paths = Option(globalExcludes),
              languages = Option(Map(Languages.Scala -> LanguageConfiguration(extensions = Option(Set(".sc")))))))
        }
      }
      val toolConfig = ToolConfiguration(
        uuid = "dat uuid fool!",
        isEnabled = true,
        notEdited = true,
        patterns = Set(
          ToolPattern(
            internalId = "dat internal id man!",
            parameters = Set(ToolParameter("dat param name", "dat param value!")))))
      val defaultIgnores = Set(PathRegex("dat regex bro!"))
      val ignoredPaths = Set(FilePath("dat file path yo!"))

      val codacyClient = new CodacyClient(apiCredentials, httpHelper)(ExecutionContext.global) {
        override def getRemoteConfiguration: Either[String, ProjectConfiguration] = {
          Right(
            ProjectConfiguration(
              ignoredPaths = ignoredPaths,
              defaultIgnores = Option(defaultIgnores),
              projectExtensions = Set(LanguageExtensions(Languages.Scala, Set(".scala", ".alacs"))),
              toolConfiguration = Set(toolConfig)))
        }
      }

      val expectedConfiguration = CLIConfiguration(
        CLIConfiguration.Analysis(
          projectDirectory = File.currentWorkingDirectory,
          output = CLIConfiguration.Output(Json.name, Option.empty),
          tool = toolInput,
          parallel = Option.empty,
          forceFilePermissions = false,
          fileExclusionRules = CLIConfiguration.FileExclusionRules(
            //although remote config has default ignores they should be discarded since there is a local config present
            defaultIgnores = Option.empty,
            ignoredPaths = ignoredPaths,
            excludePaths = CLIConfiguration.FileExclusionRules
              .ExcludePaths(global = globalExcludes, byTool = Map("engine no. 9" -> engine9Excludes)),
            allowedExtensionsByLanguage = Map(Languages.Scala -> Set(".scala", ".alacs", ".sc"))),
          toolConfiguration = CLIConfiguration.Tool(
            toolTimeout = Option.empty,
            allowNetwork = false,
            toolConfigurations = Right(
              Set(CLIConfiguration.IssuesTool(
                uuid = toolConfig.uuid,
                enabled = toolConfig.isEnabled,
                notEdited = toolConfig.notEdited,
                patterns = toolConfig.patterns.map(pattern =>
                  CLIConfiguration.IssuesTool.Pattern(
                    id = pattern.internalId,
                    parameters = pattern.parameters.map(parameter =>
                      CLIConfiguration.IssuesTool.Parameter(name = parameter.name, value = parameter.value))))))),
            extraToolConfigurations = Option(
              Map("engine no. 9" -> CLIConfiguration.IssuesTool
                .Extra(baseSubDir = engineConfig.baseSubDir, extraValues = engineConfig.extraValues))),
            extensionsByLanguage = Map(Languages.Scala -> Set(".sc")))),
        upload = CLIConfiguration.Upload(commitUuid = commitUuid, upload = false),
        result = CLIConfiguration.Result(maxAllowedIssues = 0, failIfIncomplete = false))

      val actualConfiguration = CLIConfiguration(Option(codacyClient), defaultEnvironment, defaultAnalyse, localConfig)
      unsafeRun(actualConfiguration) must beEqualTo(expectedConfiguration)
    }
  }

}
