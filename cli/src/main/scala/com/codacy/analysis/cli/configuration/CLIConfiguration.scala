package com.codacy.analysis.cli.configuration

import better.files.File
import cats.Foldable
import cats.implicits._
import com.codacy.analysis.cli.command.Analyze
import com.codacy.analysis.core.clients.CodacyClient
import com.codacy.analysis.core.clients.api._
import com.codacy.analysis.core.configuration.{
  CodacyConfigurationFile,
  CodacyConfigurationFileLoader,
  EngineConfiguration
}
import com.codacy.analysis.core.files.Glob
import com.codacy.analysis.core.git.{Commit, Git}
import com.codacy.analysis.core.utils.MapOps
import com.codacy.plugins.api.languages.Language
import play.api.libs.json.JsValue

import scala.concurrent.duration.Duration

final case class CLIConfiguration(analysis: CLIConfiguration.Analysis,
                                  upload: CLIConfiguration.Upload,
                                  result: CLIConfiguration.Result)

object CLIConfiguration {

  // HACK: Fixes Intellij IDEA highlight problems
  private type EitherA[A] = Either[String, A]
  private val foldable: Foldable[EitherA] = implicitly[Foldable[EitherA]]

  final case class Analysis(projectDirectory: File,
                            output: CLIConfiguration.Output,
                            tool: Option[String],
                            parallel: Option[Int],
                            forceFilePermissions: Boolean,
                            fileExclusionRules: CLIConfiguration.FileExclusionRules,
                            toolConfiguration: CLIConfiguration.Tool,
                            tmpDirectory: Option[File] = None,
                            maxToolMemory: Option[String] = None,
                            registryAddress: String = "")

  object Analysis {

    def apply(projectDirectory: File,
              analyze: Analyze,
              localConfiguration: Either[String, CodacyConfigurationFile],
              remoteProjectConfiguration: Either[String, ProjectConfiguration],
              tmpDirectory: Option[File],
              maxToolMemory: Option[String]): Analysis = {

      val fileExclusionRules =
        CLIConfiguration.FileExclusionRules(localConfiguration, remoteProjectConfiguration)
      val output = CLIConfiguration.Output(analyze.format, analyze.output, analyze.ghCodeScanningCompatValue)
      val toolConfiguration =
        CLIConfiguration.Tool(analyze, localConfiguration, remoteProjectConfiguration)
      CLIConfiguration.Analysis(
        projectDirectory,
        output,
        analyze.tool,
        analyze.parallel,
        analyze.forceFilePermissionsValue,
        fileExclusionRules,
        toolConfiguration,
        tmpDirectory,
        maxToolMemory,
        analyze.registryAddress)
    }
  }

  final case class Upload(commitUuid: Option[Commit.Uuid], upload: Boolean, batchSize: Int)
  final case class Result(maxAllowedIssues: Int, failIfIncomplete: Boolean)

  final case class Output(format: String, file: Option[File], ghCodeScanningCompat: Boolean)

  final case class FileExclusionRules(defaultIgnores: Option[Set[PathRegex]],
                                      ignoredPaths: Set[FilePath],
                                      excludePaths: FileExclusionRules.ExcludePaths,
                                      allowedExtensionsByLanguage: Map[Language, Set[String]])

  object FileExclusionRules {

    final case class ExcludePaths(global: Set[Glob], byTool: Map[String, Set[Glob]])

    def apply(localConfiguration: Either[String, CodacyConfigurationFile],
              remoteProjectConfiguration: Either[String, ProjectConfiguration]): FileExclusionRules = {
      val defaultIgnores: Option[Set[PathRegex]] =
        foldable.foldMap(remoteProjectConfiguration)(remoteConfig =>
          localConfiguration.fold(_ => Some(remoteConfig.defaultIgnores.getOrElse(Set.empty)), _ => None))

      val ignoredPaths: Set[FilePath] = foldable.foldMap(remoteProjectConfiguration)(_.ignoredPaths)

      val excludeByTool: Map[String, Set[Glob]] =
        foldable.foldMap(localConfiguration)(
          localConfig =>
            localConfig.engines.fold(Map.empty[String, Set[Glob]])(
              _.mapValues(_.excludePaths.getOrElse(Set.empty[Glob]))))
      val excludeGlobal =
        foldable.foldMap(localConfiguration)(_.excludePaths.getOrElse(Set.empty[Glob]))
      val excludePaths = ExcludePaths(excludeGlobal, excludeByTool)

      val localCustomExtensionsByLanguage =
        localConfiguration.map(_.languageCustomExtensions).getOrElse(Map.empty)
      val remoteCustomExtensionsByLanguage: Map[Language, Set[String]] =
        foldable.foldMap(remoteProjectConfiguration)(
          _.projectExtensions.map(le => (le.language, le.extensions))(collection.breakOut))

      val allowedExtensionsByLanguage =
        MapOps.merge(localCustomExtensionsByLanguage, remoteCustomExtensionsByLanguage)

      FileExclusionRules(defaultIgnores, ignoredPaths, excludePaths, allowedExtensionsByLanguage)
    }

    implicit def toCollectorExclusionRules(
      rules: CLIConfiguration.FileExclusionRules): com.codacy.analysis.core.files.FileExclusionRules = {
      com.codacy.analysis.core.files.FileExclusionRules(
        rules.defaultIgnores,
        rules.ignoredPaths,
        com.codacy.analysis.core.files.ExcludePaths(rules.excludePaths.global, rules.excludePaths.byTool),
        rules.allowedExtensionsByLanguage)
    }
  }

  final case class Tool(toolTimeout: Option[Duration],
                        allowNetwork: Boolean,
                        toolConfigurations: Either[String, Set[CLIConfiguration.IssuesTool]],
                        extraToolConfigurations: Option[Map[String, CLIConfiguration.IssuesTool.Extra]],
                        extensionsByLanguage: Map[Language, Set[String]])

  object Tool {

    def apply(analyze: Analyze,
              localConfiguration: Either[String, CodacyConfigurationFile],
              remoteProjectConfiguration: Either[String, ProjectConfiguration]): CLIConfiguration.Tool = {

      val enginesConfiguration = for {
        config <- localConfiguration.toOption
        engines <- config.engines
      } yield CLIConfiguration.IssuesTool.extraFromApi(engines)
      val toolConfigurations: Either[String, Set[CLIConfiguration.IssuesTool]] = for {
        projectConfig <- remoteProjectConfiguration
        toolConfigs = projectConfig.toolConfiguration
      } yield CLIConfiguration.IssuesTool.fromApi(toolConfigs)

      val languageExtensions: Map[Language, Set[String]] =
        localConfiguration.map(_.languageCustomExtensions).getOrElse(Map.empty[Language, Set[String]])

      CLIConfiguration.Tool(
        analyze.toolTimeout,
        analyze.allowNetworkValue,
        toolConfigurations,
        enginesConfiguration,
        languageExtensions)
    }
  }

  final case class IssuesTool(uuid: String,
                              enabled: Boolean,
                              notEdited: Boolean,
                              patterns: Set[CLIConfiguration.IssuesTool.Pattern])

  object IssuesTool {
    final case class Extra(baseSubDir: Option[String], extraValues: Option[Map[String, JsValue]])
    final case class Pattern(id: String, parameters: Set[CLIConfiguration.IssuesTool.Parameter])
    final case class Parameter(name: String, value: String)

    def extraFromApi(apiEngines: Map[String, EngineConfiguration]): Map[String, CLIConfiguration.IssuesTool.Extra] = {
      apiEngines.mapValues(config => CLIConfiguration.IssuesTool.Extra(config.baseSubDir, config.extraValues))
    }

    def fromApi(apiToolConfigs: Set[ToolConfiguration]): Set[CLIConfiguration.IssuesTool] = {
      apiToolConfigs.map { toolConfig =>
        CLIConfiguration.IssuesTool(
          uuid = toolConfig.uuid,
          enabled = toolConfig.isEnabled,
          notEdited = toolConfig.notEdited,
          patterns = toolConfig.patterns.map { pattern =>
            CLIConfiguration.IssuesTool.Pattern(
              id = pattern.internalId,
              parameters = pattern.parameters.map { param =>
                CLIConfiguration.IssuesTool.Parameter(param.name, param.value)
              })
          })
      }
    }

    private def toInternalParameter(
      parameter: CLIConfiguration.IssuesTool.Parameter): com.codacy.analysis.core.model.Parameter = {
      com.codacy.analysis.core.model.Parameter(parameter.name, parameter.value)
    }

    def toInternalPattern(pattern: CLIConfiguration.IssuesTool.Pattern): com.codacy.analysis.core.model.Pattern = {
      com.codacy.analysis.core.model.Pattern(pattern.id, pattern.parameters.map(toInternalParameter))
    }
  }

  def apply(clientOpt: Option[CodacyClient],
            environment: Environment,
            analyze: Analyze,
            localConfigLoader: CodacyConfigurationFileLoader): CLIConfiguration = {
    val projectDirectory: File = environment.baseProjectDirectory(analyze.directory)
    val commitUuid: Option[Commit.Uuid] =
      analyze.commitUuid.orElse(Git.currentCommitUuid(projectDirectory))

    val localConfiguration: Either[String, CodacyConfigurationFile] =
      localConfigLoader.load(projectDirectory)
    val remoteProjectConfiguration: Either[String, ProjectConfiguration] = clientOpt.fold {
      "No credentials found.".asLeft[ProjectConfiguration]
    } {
      _.getRemoteConfiguration
    }
    val analysisConfiguration =
      Analysis(
        projectDirectory,
        analyze,
        localConfiguration,
        remoteProjectConfiguration,
        analyze.tmpDirectory,
        analyze.maxToolMemory)
    val uploadConfiguration = Upload(commitUuid, analyze.uploadValue, analyze.uploadBatchSize)
    val resultConfiguration = Result(analyze.maxAllowedIssues, analyze.failIfIncompleteValue)

    CLIConfiguration(analysisConfiguration, uploadConfiguration, resultConfiguration)
  }

}
