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
import com.codacy.analysis.core.utils.MapOps
import com.codacy.plugins.api.languages.Language
import play.api.libs.json.JsValue

import scala.concurrent.duration.Duration

case class CLIProperties(analysis: AnalysisProperties, upload: UploadProperties, result: ResultProperties)

object CLIProperties {

  // HACK: Fixes Intellij IDEA highlight problems
  private type EitherA[A] = Either[String, A]
  private val foldable: Foldable[EitherA] = implicitly[Foldable[EitherA]]

  case class AnalysisProperties(projectDirectory: File,
                                output: AnalysisProperties.Output,
                                tool: Option[String],
                                parallel: Option[Int],
                                forceFilePermissions: Boolean,
                                fileExclusionRules: AnalysisProperties.FileExclusionRules,
                                toolProperties: AnalysisProperties.Tool)

  object AnalysisProperties {

    case class Tool(toolTimeout: Option[Duration],
                    allowNetwork: Boolean,
                    toolConfigurations: Either[String, Set[Tool.IssuesToolConfiguration]],
                    extraToolConfigurations: Option[Map[String, Tool.IssuesToolConfiguration.Extra]],
                    extensionsByLanguage: Map[Language, Set[String]])

    object Tool {

      case class IssuesToolConfiguration(uuid: String,
                                         enabled: Boolean,
                                         notEdited: Boolean,
                                         patterns: Set[IssuesToolConfiguration.Pattern])

      object IssuesToolConfiguration {
        case class Extra(baseSubDir: Option[String], extraValues: Option[Map[String, JsValue]])
        case class Pattern(id: String, parameters: Set[IssuesToolConfiguration.Parameter])
        case class Parameter(name: String, value: String)

        def extraFromApi(engines: Map[String, EngineConfiguration]): Map[String, IssuesToolConfiguration.Extra] = {
          engines.mapValues(config => new IssuesToolConfiguration.Extra(config.baseSubDir, config.extraValues))
        }

        def fromApi(toolConfigs: Set[ToolConfiguration]): Set[IssuesToolConfiguration] = {
          toolConfigs.map { toolConfig =>
            IssuesToolConfiguration(
              uuid = toolConfig.uuid,
              enabled = toolConfig.isEnabled,
              notEdited = toolConfig.notEdited,
              patterns = toolConfig.patterns.map { pattern =>
                IssuesToolConfiguration.Pattern(id = pattern.internalId, parameters = pattern.parameters.map { param =>
                  IssuesToolConfiguration.Parameter(param.name, param.value)
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
        AnalysisProperties.Tool(
          analyse.toolTimeout,
          analyse.allowNetworkValue,
          toolConfigurations,
          enginesConfiguration,
          languageExtensions)
      }
    }

    case class FileExclusionRules(defaultIgnores: Option[Set[PathRegex]],
                                  ignoredPaths: Set[FilePath],
                                  excludePaths: FileExclusionRules.ExcludePaths,
                                  allowedExtensionsByLanguage: Map[Language, Set[String]])

    object FileExclusionRules {

      case class ExcludePaths(global: Set[Glob], byTool: Map[String, Set[Glob]])

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
        rules: AnalysisProperties.FileExclusionRules): com.codacy.analysis.core.files.FileExclusionRules = {
        com.codacy.analysis.core.files.FileExclusionRules(
          rules.defaultIgnores,
          rules.ignoredPaths,
          com.codacy.analysis.core.files.ExcludePaths(rules.excludePaths.global, rules.excludePaths.byTool),
          rules.allowedExtensionsByLanguage)
      }
    }

    case class Output(format: String, file: Option[File])

    def apply(projectDirectory: File,
              analyse: Analyse,
              localConfiguration: Either[String, CodacyConfigurationFile],
              remoteProjectConfiguration: Either[String, ProjectConfiguration]): AnalysisProperties = {

      val fileExclusionRules = AnalysisProperties.FileExclusionRules(localConfiguration, remoteProjectConfiguration)
      val output = AnalysisProperties.Output(analyse.format, analyse.output)
      val toolProperties = AnalysisProperties.Tool(analyse, localConfiguration, remoteProjectConfiguration)
      AnalysisProperties(
        projectDirectory,
        output,
        analyse.tool,
        analyse.parallel,
        analyse.forceFilePermissionsValue,
        fileExclusionRules,
        toolProperties)
    }
  }

  case class UploadProperties(commitUuid: Option[Commit.Uuid], upload: Boolean)
  case class ResultProperties(maxAllowedIssues: Int, failIfIncomplete: Boolean)

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
    val uploadProperties = UploadProperties(commitUuid, analyse.uploadValue)
    val resultProperties = ResultProperties(analyse.maxAllowedIssues, analyse.failIfIncompleteValue)

    CLIProperties(analysisProperties, uploadProperties, resultProperties)
  }

}
