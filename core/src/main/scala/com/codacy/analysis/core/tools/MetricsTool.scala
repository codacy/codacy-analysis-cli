package com.codacy.analysis.core.tools

import java.nio.file.Paths
import better.files.File
import com.codacy.analysis.core.model.{AnalyserError, FileMetrics, MetricsToolSpec}
import com.codacy.plugins.api
import com.codacy.plugins.api.{Source, metrics}
import com.codacy.plugins.api.languages.Language
import com.codacy.plugins.api.metrics.MetricsTool.CodacyConfiguration
import com.codacy.plugins.metrics.traits
import com.codacy.plugins.metrics.traits.{MetricsRequest, MetricsRunner}
import com.codacy.plugins.runners.{BinaryDockerRunner, DockerRunner}
import org.log4s.getLogger

import scala.concurrent.duration.Duration
import scala.util.Try

class MetricsTool(metricsToolSpec: MetricsToolSpec, val languageToRun: Language, registryAddress: String)
    extends ITool {
  override def name: String = "metrics"

  override def supportedLanguages: Set[Language] = metricsToolSpec.languages.to[Set]

  def run(directory: File,
          files: Set[Source.File],
          tmpDirectory: Option[File] = None,
          timeout: Option[Duration] = Option.empty[Duration],
          maxToolMemory: Option[String] = None): Try[List[FileMetrics]] = {
    val request = MetricsRequest(directory.pathAsString)

    val metricsTool =
      new traits.MetricsTool(registryAddress + metricsToolSpec.dockerImage, metricsToolSpec.languages.toList)

    val dockerRunner = new BinaryDockerRunner[api.metrics.FileMetrics](
      metricsTool,
      BinaryDockerRunner.Config(containerMemoryLimit = maxToolMemory))
    val runner = new MetricsRunner(metricsTool, dockerRunner)

    val configuration = CodacyConfiguration(Some(files), Some(languageToRun), None)

    // protect not running metrics tools when files are empty.
    // the normal linter tools are already protected against this case and
    // are not called when that happens.
    val toolFileMetrics =
      if (files.isEmpty) {
        Try(List.empty[metrics.FileMetrics])
      } else {
        runner.run(
          request,
          configuration,
          timeout.getOrElse(DockerRunner.defaultRunTimeout),
          dockerConfig = None,
          tmpDirectory.map(_.toJava))
      }

    toolFileMetrics.map {
      _.collect {
        case fileMetrics if unignoredFile(fileMetrics, Some(files)) =>
          FileMetrics(
            filename = Paths.get(fileMetrics.filename),
            complexity = fileMetrics.complexity,
            loc = fileMetrics.loc,
            cloc = fileMetrics.cloc,
            nrMethods = fileMetrics.nrMethods,
            nrClasses = fileMetrics.nrClasses,
            lineComplexities = fileMetrics.lineComplexities)
      }
    }
  }

  def unignoredFile(metrics: api.metrics.FileMetrics, files: Option[Set[Source.File]]): Boolean = {
    files.forall(_.exists(_.path == metrics.filename))
  }
}

class MetricsToolCollector(toolRepository: ToolRepository) {

  private val logger: org.log4s.Logger = getLogger

  def fromLanguages(languages: Set[Language], registryAddress: String): Either[AnalyserError, Set[MetricsTool]] = {
    toolRepository.listMetricsTools().map { tools =>
      languages.flatMap { lang =>
        val collectedTools = tools.collect {
          case tool if tool.languages.contains(lang) =>
            new MetricsTool(tool, lang, registryAddress)
        }

        if (collectedTools.isEmpty) {
          logger.info(s"No metrics tools found for language ${lang.name}")
        }

        collectedTools
      }
    }
  }

}
