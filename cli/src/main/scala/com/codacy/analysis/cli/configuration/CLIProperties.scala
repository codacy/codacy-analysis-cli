package com.codacy.analysis.cli.configuration

import better.files.File
import cats.Foldable
import cats.implicits._
import com.codacy.analysis.cli.command.Analyse
import com.codacy.analysis.cli.configuration.CLIProperties.{AnalysisProperties, ResultProperties, UploadProperties}
import com.codacy.analysis.core.clients.CodacyClient
import com.codacy.analysis.core.clients.api._
import com.codacy.analysis.core.configuration.{CodacyConfigurationFile, EngineConfiguration}
import com.codacy.analysis.core.files.Glob
import com.codacy.analysis.core.git.{Commit, Git}
import com.codacy.plugins.api.languages.Language
import play.api.libs.json.JsValue

import scala.concurrent.duration.Duration

class CLIProperties(val analysis: AnalysisProperties, val upload: UploadProperties, val result: ResultProperties)

object CLIProperties {

  // HACK: Fixes Intellij IDEA highlight problems
  private type EitherA[A] = Either[String, A]
  private val foldable: Foldable[EitherA] = implicitly[Foldable[EitherA]]

  class AnalysisProperties(val projectDirectory: File,
                           val output: AnalysisProperties.Output,
                           val tool: Option[String],
                           val parallel: Option[Int],
                           val forceFilePermissions: Boolean,
                           val fileExclusionRules: AnalysisProperties.FileExclusionRules,
                           val toolProperties: AnalysisProperties.Tool)

  object AnalysisProperties {

    class Tool(val toolTimeout: Option[Duration],
               val allowNetwork: Boolean,
               val toolConfigurations: Either[String, Set[Tool.IssuesToolConfiguration]],
               val extraToolConfigurations: Option[Map[String, Tool.IssuesToolConfiguration.Extra]],
               val localExtensionsByLanguage: Map[Language, Set[String]])

    object Tool {

      class IssuesToolConfiguration(val uuid: String,
                                    val enabled: Boolean,
                                    val notEdited: Boolean,
                                    val patterns: Set[IssuesToolConfiguration.Pattern])

      object IssuesToolConfiguration {
        class Extra(val baseSubDir: Option[String], val extraValues: Option[Map[String, JsValue]])
        class Pattern(val id: String, val parameters: Set[IssuesToolConfiguration.Parameter])
        class Parameter(val name: String, val value: String)

        def extraFromApi(engines: Map[String, EngineConfiguration]): Map[String, IssuesToolConfiguration.Extra] = {
          engines.mapValues(config => new IssuesToolConfiguration.Extra(config.baseSubDir, config.extraValues))
        }

        def fromApi(toolConfigs: Set[ToolConfiguration]): Set[IssuesToolConfiguration] = {
          toolConfigs.map { toolConfig =>
            new IssuesToolConfiguration(
              uuid = toolConfig.uuid,
              enabled = toolConfig.isEnabled,
              notEdited = toolConfig.notEdited,
              patterns = toolConfig.patterns.map { pattern =>
                new IssuesToolConfiguration.Pattern(id = pattern.internalId, parameters = pattern.parameters.map {
                  param =>
                    new IssuesToolConfiguration.Parameter(param.name, param.value)
                })
              })
          }
        }

        private def toInternalParameter(
          parameter: IssuesToolConfiguration.Parameter): com.codacy.analysis.core.model.Parameter = {
          com.codacy.analysis.core.model.Parameter(parameter.name, parameter.value)
        }

        def toInternalPattern(pattern: IssuesToolConfiguration.Pattern): com.codacy.analysis.core.model.Pattern = {
          com.codacy.analysis.core.model.Pattern(pattern.id, pattern.parameters.map(toInternalParameter))
        }
      }

      def apply(analyse: Analyse,
                localConfiguration: Either[String, CodacyConfigurationFile],
                remoteProjectConfiguration: Either[String, ProjectConfiguration]): AnalysisProperties.Tool = {
        val enginesConfiguration = for {
          config <- localConfiguration.toOption
          engines <- config.engines
        } yield IssuesToolConfiguration.extraFromApi(engines)
        val toolConfigurations: Either[String, Set[AnalysisProperties.Tool.IssuesToolConfiguration]] = for {
          projectConfig <- remoteProjectConfiguration
          toolConfigs = projectConfig.toolConfiguration
        } yield IssuesToolConfiguration.fromApi(toolConfigs)

        val languageExtensions: Map[Language, Set[String]] =
          localConfiguration.map(_.languageCustomExtensions).getOrElse(Map.empty[Language, Set[String]])
        new AnalysisProperties.Tool(
          analyse.toolTimeout,
          analyse.allowNetworkValue,
          toolConfigurations,
          enginesConfiguration,
          languageExtensions)
      }
    }

    class FileExclusionRules(val defaultIgnores: Option[Set[PathRegex]],
                             val ignoredPaths: Set[FilePath],
                             val excludePaths: FileExclusionRules.ExcludePaths,
                             val allowedExtensionsByLanguage: Map[Language, Set[String]])

    object FileExclusionRules {

      class ExcludePaths(val global: Set[Glob], val byTool: Map[String, Set[Glob]])

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
                _.mapValues(_.exclude_paths.getOrElse(Set.empty[Glob]))))
        val excludeGlobal = foldable.foldMap(localConfiguration)(_.exclude_paths.getOrElse(Set.empty[Glob]))
        val excludePaths = new ExcludePaths(excludeGlobal, excludeByTool)

        val localCustomExtensionsByLanguage =
          localConfiguration.map(_.languageCustomExtensions).getOrElse(Map.empty)
        val remoteCustomExtensionsByLanguage: Map[Language, Set[String]] =
          foldable.foldMap(remoteProjectConfiguration)(
            _.projectExtensions.map(le => (le.language, le.extensions))(collection.breakOut))
        val allowedExtensionsByLanguage = localCustomExtensionsByLanguage ++ remoteCustomExtensionsByLanguage

        new FileExclusionRules(defaultIgnores, ignoredPaths, excludePaths, allowedExtensionsByLanguage)
      }

      implicit def toCollectorExclusionRules(
        rules: AnalysisProperties.FileExclusionRules): com.codacy.analysis.core.files.FileExclusionRules = {
        com.codacy.analysis.core.files.FileExclusionRules(
          rules.defaultIgnores,
          rules.ignoredPaths,
          com.codacy.analysis.core.files.ExcludePaths(rules.excludePaths.global, rules.excludePaths.byTool),
          rules.allowedExtensionsByLanguage)
      }
    }

    class Output(val format: String, val file: Option[File])

    def apply(projectDirectory: File,
              analyse: Analyse,
              localConfiguration: Either[String, CodacyConfigurationFile],
              remoteProjectConfiguration: Either[String, ProjectConfiguration]): AnalysisProperties = {

      val fileExclusionRules = AnalysisProperties.FileExclusionRules(localConfiguration, remoteProjectConfiguration)
      val output = new AnalysisProperties.Output(analyse.format, analyse.output)
      val toolProperties = AnalysisProperties.Tool(analyse, localConfiguration, remoteProjectConfiguration)
      new AnalysisProperties(
        projectDirectory,
        output,
        analyse.tool,
        analyse.parallel,
        analyse.forceFilePermissionsValue,
        fileExclusionRules,
        toolProperties)
    }
  }

  class UploadProperties(val commitUuid: Option[Commit.Uuid], val upload: Boolean)
  class ResultProperties(val maxAllowedIssues: Int, val failIfIncomplete: Boolean)

  def apply(clientOpt: Option[CodacyClient],
            environment: Environment,
            analyse: Analyse,
            loadLocalConfig: File => Either[String, CodacyConfigurationFile]): CLIProperties = {
    val projectDirectory: File = environment.baseProjectDirectory(analyse.directory)
    val commitUuid: Option[Commit.Uuid] =
      analyse.commitUuid.orElse(Git.currentCommitUuid(projectDirectory))

    val localConfiguration: Either[String, CodacyConfigurationFile] = loadLocalConfig(projectDirectory)
    val remoteProjectConfiguration: Either[String, ProjectConfiguration] = clientOpt.fold {
      "No credentials found.".asLeft[ProjectConfiguration]
    } {
      _.getRemoteConfiguration
    }
    val analysisProperties =
      AnalysisProperties(projectDirectory, analyse, localConfiguration, remoteProjectConfiguration)
    val uploadProperties = new UploadProperties(commitUuid, analyse.uploadValue)
    val resultProperties = new ResultProperties(analyse.maxAllowedIssues, analyse.failIfIncompleteValue)

    new CLIProperties(analysisProperties, uploadProperties, resultProperties)
  }

}
