package com.codacy.analysis.cli.command.analyse
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor.MetricsToolExecutorResult
import com.codacy.analysis.core.model.FileMetrics
import org.log4s.{Logger, getLogger}

import scala.util.{Failure, Success}

object MetricsToolExecutor {

  private val logger: Logger = getLogger

  def reduceMetricsToolResultsByFile(metricsResults: Seq[MetricsToolExecutorResult]): Seq[MetricsToolExecutorResult] = {
    metricsResults
      .groupBy(_.language)
      .values
      .flatMap {
        val errorLogMessage =
          "While merging metrics results, found a success and a failure for the same file. Will assume the success."
        _.foldLeft(Option.empty[MetricsToolExecutorResult]) {
          case (
              Some(metricsExecutorResAcc @ MetricsToolExecutorResult(_, _, Success(fileMetricsAcc))),
              metricsExecutorRes @ MetricsToolExecutorResult(_, _, Success(fileMetrics))) =>
            val allFiles = metricsExecutorResAcc.files ++ metricsExecutorRes.files
            val reducedFileMetrics = reduceFileMetricsByFile(fileMetrics ++ fileMetricsAcc)
            Some(metricsExecutorResAcc.copy(files = allFiles, analysisResults = Success(reducedFileMetrics)))
          //TODO (FT-5881): we need to find a way to return the failures to backend so we can manage to distinguish between PartialFailures and metrics that don't have complexity
          case (
              metricsToolExecutorRes @ Some(MetricsToolExecutorResult(_, _, Success(_))),
              MetricsToolExecutorResult(_, _, Failure(_))) =>
            logger.error(errorLogMessage)
            metricsToolExecutorRes
          case (
              Some(MetricsToolExecutorResult(_, _, Failure(_))),
              metricsToolExecutorRes @ MetricsToolExecutorResult(_, _, Success(_))) =>
            logger.error(errorLogMessage)
            Some(metricsToolExecutorRes)
          case (_, o) => Some(o)
        }
      }(collection.breakOut)
  }

  private def reduceFileMetricsByFile(fileMetrics: Set[FileMetrics]): Set[FileMetrics] = {
    fileMetrics
      .groupBy(_.filename)
      .map {
        case (filePath, fMetrics) =>
          fMetrics.reduce { (fMetricsAccumulator, fMetricsElement) =>
            FileMetrics(
              filePath,
              fMetricsAccumulator.complexity.orElse(fMetricsElement.complexity),
              fMetricsAccumulator.loc.orElse(fMetricsElement.loc),
              fMetricsAccumulator.cloc.orElse(fMetricsElement.cloc),
              fMetricsAccumulator.nrMethods.orElse(fMetricsElement.nrMethods),
              fMetricsAccumulator.nrClasses.orElse(fMetricsElement.nrClasses),
              if (fMetricsAccumulator.lineComplexities.nonEmpty) {
                fMetricsAccumulator.lineComplexities
              } else {
                fMetricsElement.lineComplexities
              })
          }
      }(collection.breakOut)
  }
}
