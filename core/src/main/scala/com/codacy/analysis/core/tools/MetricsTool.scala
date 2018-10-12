package com.codacy.analysis.core.tools

import java.nio.file.Paths

import better.files.File
import com.codacy.analysis.core.model.FileMetrics
import com.codacy.analysis.core.utils.IOHelper.IOThrowable
import com.codacy.plugins.api
import com.codacy.plugins.api.{Source, metrics}
import com.codacy.plugins.api.languages.Language
import com.codacy.plugins.api.metrics.MetricsTool.CodacyConfiguration
import com.codacy.plugins.metrics.traits
import com.codacy.plugins.metrics.traits.{MetricsRequest, MetricsRunner}
import com.codacy.plugins.traits.{BinaryDockerRunner, DockerRunner}
import com.codacy.plugins.utils.PluginHelper
import org.log4s.getLogger
import scalaz.zio.IO

import scala.concurrent.duration.Duration
import scala.util.Try

class MetricsTool(private val metricsTool: traits.MetricsTool, val languageToRun: Language) extends ITool {
  override def name: String = "metrics"

  override def supportedLanguages: Set[Language] = metricsTool.languages.to[Set]

  def run(directory: File,
          files: Option[Set[Source.File]],
          timeout: Option[Duration] = Option.empty[Duration]): IOThrowable[List[FileMetrics]] = {
    val request = MetricsRequest(directory.pathAsString)

    val dockerRunner = new BinaryDockerRunner[api.metrics.FileMetrics](metricsTool)
    val runner = new MetricsRunner(metricsTool, dockerRunner)

    val configuration = CodacyConfiguration(files, Some(languageToRun), None)

    def run(): Try[List[metrics.FileMetrics]] = {
      runner.run(request, configuration, timeout.getOrElse(DockerRunner.defaultRunTimeout), None)
    }

    for {
      _ <- IO.point(())
      fileMetricsList <- IO.fromTry(run())
    } yield {
      fileMetricsList.collect {
        case fileMetrics if unignoredFile(fileMetrics, files) =>
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

object MetricsToolCollector {

  private val logger: org.log4s.Logger = getLogger

  private val availableTools = PluginHelper.dockerMetricsPlugins

  def fromLanguages(languages: Set[Language]): Set[MetricsTool] = {
    languages.flatMap { lang =>
      val collectedTools = availableTools.collect {
        case tool if tool.languages.contains(lang) =>
          new MetricsTool(tool, lang)
      }
      if (collectedTools.isEmpty) {
        logger.info(s"No metrics tools found for language ${lang.name}")
      }
      collectedTools
    }
  }

}
