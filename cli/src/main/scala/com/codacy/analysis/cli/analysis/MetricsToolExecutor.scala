package com.codacy.analysis.cli.analysis

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.core
import com.codacy.analysis.core.model.FileMetrics

import scala.util.{Failure, Success}

object MetricsToolExecutor {

  import com.codacy.analysis.cli.analysis.AnalyseExecutor._

  def reduceMetricsToolResultsByFile(metricsResults: Seq[MetricsToolExecutorResult]): Seq[MetricsToolExecutorResult] = {
    val (successfulMetricsResults, failedMetricsResults) =
      metricsResults.partition(_.analysisResults.isSuccess)

    successfulMetricsResults
      .groupBy(_.language)
      .values
      .flatMap {
        _.foldLeft(Option.empty[MetricsToolExecutorResult]) {
          case (
                Some(metricsExecutorResAcc @ MetricsToolExecutorResult(_, _, Success(fileMetricsAcc))),
                metricsExecutorRes @ MetricsToolExecutorResult(_, _, Success(fileMetrics))) =>
            val allFiles = metricsExecutorResAcc.files ++ metricsExecutorRes.files
            val reducedFileMetrics = reduceFileMetricsByFile(fileMetrics ++ fileMetricsAcc)
            Some(metricsExecutorResAcc.copy(files = allFiles, analysisResults = Success(reducedFileMetrics)))
          case (_, o) => Some(o)
        }
      }(collection.breakOut) ++ failedMetricsResults
  }

  private def reduceFileMetricsByFile(fileMetrics: Set[FileMetrics]): Set[FileMetrics] = {
    fileMetrics
      .groupBy(_.filename)
      .flatMap {
        case (filePath, fMetrics) =>
          fMetrics.reduceOption { (fMetricsAccumulator, fMetricsElement) =>
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

  def calculateMissingFileMetrics(
    directory: File,
    metricsResults: Seq[AnalyseExecutor.MetricsToolExecutorResult]): Seq[MetricsToolExecutorResult] = {

    val fileMetricsByFilePath: Map[Path, FileMetrics] = metricsResults.flatMap { result =>
      result.analysisResults.map(_.map(fileMetrics => (fileMetrics.filename, fileMetrics))).getOrElse(Set.empty)
    }(collection.breakOut)

    metricsResults.foldLeft(Seq.empty[MetricsToolExecutorResult]) {
      case (metricsAccumulator, res @ AnalyseExecutor.MetricsToolExecutorResult(_, _, Success(_))) =>
        metricsAccumulator :+ countMissingLoc(directory, fileMetricsByFilePath, res)
      case (metricsAccumulator, res @ AnalyseExecutor.MetricsToolExecutorResult(lang, files, Failure(_)))
          if !metricsResults.exists(r => r.language == lang && r.files == files && r.analysisResults.isSuccess) =>
        metricsAccumulator :+ res :+ countMissingLoc(directory, fileMetricsByFilePath, res)
      case (metricsAccumulator, res) =>
        metricsAccumulator :+ res
    }
  }

  private def countMissingLoc(directory: File,
                              fileMetricsByFilePath: Map[Path, FileMetrics],
                              metricsRes: AnalyseExecutor.MetricsToolExecutorResult): MetricsToolExecutorResult = {
    val fileMetrics = metricsRes.files.map { file =>
      fileMetricsByFilePath.get(file) match {
        case None =>
          FileMetrics(
            filename = file,
            nrClasses = None,
            nrMethods = None,
            loc = countLoc(directory, file),
            cloc = None,
            complexity = None,
            lineComplexities = Set.empty)
        case Some(metrics) if metrics.loc.isEmpty => metrics.copy(loc = countLoc(directory, file))
        case Some(metrics)                        => metrics
      }
    }
    metricsRes.copy(analysisResults = Success(fileMetrics))
  }

  private def countLoc(directory: File, file: Path): Option[Int] = {
    val fileAbsolutePath = (directory / file.toString).path.toAbsolutePath.toString
    core.utils.FileHelper.countLoc(fileAbsolutePath)
  }
}
