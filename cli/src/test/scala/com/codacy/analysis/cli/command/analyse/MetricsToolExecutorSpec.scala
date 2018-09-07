package com.codacy.analysis.cli.command.analyse
import java.nio.file.Paths

import better.files.File
import com.codacy.analysis.cli.command.analyse.AnalyseExecutor.MetricsToolExecutorResult
import com.codacy.analysis.core.model.FileMetrics
import com.codacy.plugins.api.languages.Languages
import com.codacy.plugins.api.languages.Languages.Java
import com.codacy.plugins.api.metrics.LineComplexity
import org.specs2.mutable.Specification

import scala.collection.immutable.Set
import scala.util.Success

class MetricsToolExecutorSpec extends Specification {

  "MetricsToolExecutor" should {
    "reduce metrics file executor result to one if there are multiple file results for the same file" in {
      val file1 = Paths.get("test1.java")
      val file2 = Paths.get("test2.java")
      //Given
      val metricsResults: Seq[MetricsToolExecutorResult] = Seq(
        MetricsToolExecutorResult(
          Java.name,
          Set(file1, file2),
          Success(
            Set(
              FileMetrics(
                filename = file2,
                complexity = None,
                loc = Some(2),
                cloc = Some(2),
                nrMethods = None,
                nrClasses = None,
                lineComplexities = Set.empty),
              FileMetrics(
                filename = file1,
                complexity = Some(1),
                loc = None,
                cloc = None,
                nrMethods = Some(2),
                nrClasses = Some(3),
                lineComplexities = Set(LineComplexity(1, 2)))))),
        MetricsToolExecutorResult(
          Java.name,
          Set(file1, file2),
          Success(
            Set(
              FileMetrics(
                filename = file1,
                complexity = None,
                loc = Some(200),
                cloc = Some(222),
                nrMethods = None,
                nrClasses = None,
                lineComplexities = Set.empty),
              FileMetrics(
                filename = file2,
                complexity = Some(100),
                loc = None,
                cloc = None,
                nrMethods = Some(23),
                nrClasses = None,
                lineComplexities = Set(LineComplexity(12, 2), LineComplexity(66, 6)))))))

      //When
      val reducedMetricsResults = MetricsToolExecutor.reduceMetricsToolResultsByFile(metricsResults)

      //Then
      reducedMetricsResults must containTheSameElementsAs(
        Seq(MetricsToolExecutorResult(
          Java.name,
          Set(file1, file2),
          Success(Set(
            FileMetrics(
              filename = file1,
              complexity = Some(1),
              loc = Some(200),
              cloc = Some(222),
              nrMethods = Some(2),
              nrClasses = Some(3),
              lineComplexities = Set(LineComplexity(1, 2))),
            FileMetrics(
              filename = file2,
              complexity = Some(100),
              loc = Some(2),
              cloc = Some(2),
              nrMethods = Some(23),
              nrClasses = None,
              lineComplexities = Set(LineComplexity(12, 2), LineComplexity(66, 6))))))))
    }

    "always count the lines of code for a file, even if the metrics do not have this data" in {
      //Given
      val fileName = "test.go"
      val filePath = Paths.get(fileName)
      val goCycloFileMetrics =
        FileMetrics(filePath, Some(2), None, None, Some(2), None, Set(LineComplexity(18, 2), LineComplexity(24, 1)))

      //When
      val resultsWithMissingMetrics = MetricsToolExecutor.calculateMissingFileMetrics(
        File("cli/src/test/resources/com/codacy/analysis/cli/samples"),
        Seq(
          AnalyseExecutor
            .MetricsToolExecutorResult(Languages.Go.name, Set(filePath), Success(Set(goCycloFileMetrics)))))

      //Then
      resultsWithMissingMetrics must containTheSameElementsAs(
        Seq(
          AnalyseExecutor.MetricsToolExecutorResult(
            Languages.Go.name,
            Set(filePath),
            Success(Set(goCycloFileMetrics.copy(loc = Some(64)))))))
    }
  }
}
