package com.codacy.analysis.cli.command.analyse
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor.MetricsToolExecutorResult
import com.codacy.analysis.core.model.FileMetrics
import org.log4s.{Logger, getLogger}

import scala.util.{Failure, Success}

object MetricsToolExecutor {

  private val logger: Logger = getLogger

  //TODO: should I put here more logic related with the metrics that's on AnalyseExecutor?
  //I could also create a DuplicationToolExecutor and a IssuesToolExecutor and remove more logic from the analyse executor.

  def reduceMetricsToolResultsByLanguage(
    metricsResults: Seq[MetricsToolExecutorResult]): Seq[MetricsToolExecutorResult] = {
    metricsResults
      .groupBy(_.language)
      .flatMap {
        case (_, results) =>
          results.foldLeft(Option.empty[MetricsToolExecutorResult]) {
            case (
                Some(metricsExecutorRes1 @ MetricsToolExecutorResult(_, _, Success(fileMetrics1))),
                metricsExecutorRes2 @ MetricsToolExecutorResult(_, _, Success(fileMetrics2))) =>
              Some(
                metricsExecutorRes1.copy(
                  files = metricsExecutorRes1.files ++ metricsExecutorRes2.files,
                  analysisResults = Success(reduceFileMetricsByFile(fileMetrics1 ++ fileMetrics2))))
            //TODO: we need to find a way to return the failures to backend so we can manage to distinguish between PartialFailures and metrics that don't have complexity
            case (
                metricsToolExecutorRes @ Some(MetricsToolExecutorResult(_, _, Success(_))),
                MetricsToolExecutorResult(_, _, Failure(e))) =>
              logger.error(e)("Failed to run metrics.")
              metricsToolExecutorRes
            case (
                Some(MetricsToolExecutorResult(_, _, Failure(e))),
                metricsToolExecutorRes @ MetricsToolExecutorResult(_, _, Success(_))) =>
              logger.error(e)("Failed to run metrics.")
              Some(metricsToolExecutorRes)
            case (_, o) => Some(o)
          }
      }(collection.breakOut)
  }

  //TODO: this should be on core. It should be moved there when the logic to calculate the missing metrics is done
  //(that will also be there)
  private def reduceFileMetricsByFile(fileMetrics: Set[FileMetrics]): Set[FileMetrics] = {
    fileMetrics
      .groupBy(_.filename)
      .map {
        case (filePath, fmSet) =>
          fmSet.reduce { (fm1, fm2) =>
            FileMetrics(
              filePath,
              fm1.complexity.orElse(fm2.complexity),
              fm1.loc.orElse(fm2.loc),
              fm1.cloc.orElse(fm2.cloc),
              fm1.nrMethods.orElse(fm2.nrMethods),
              fm1.nrClasses.orElse(fm2.nrClasses),
              if (fm1.lineComplexities.nonEmpty) fm1.lineComplexities else fm2.lineComplexities)
          }
      }(collection.breakOut)
  }
}
