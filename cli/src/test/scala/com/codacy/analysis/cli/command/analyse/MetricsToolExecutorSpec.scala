package com.codacy.analysis.cli.command.analyse

import java.nio.file.Paths

import better.files.File
import com.codacy.analysis.cli.analysis.AnalyseExecutor.MetricsToolExecutorResult
import com.codacy.analysis.cli.analysis.{AnalyseExecutor, MetricsToolExecutor}
import com.codacy.analysis.core.model.FileMetrics
import com.codacy.plugins.api.languages.Languages
import com.codacy.plugins.api.languages.Languages._
import com.codacy.plugins.api.metrics.LineComplexity
import org.specs2.mutable.Specification

import scala.collection.immutable.Set
import scala.util.{Failure, Success}

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

    "count the lines of code for all the files, even if there are failures for some metrics" in {
      //Given
      val throwable = new Throwable("FAILED!")

      val test1JsPath = Paths.get("test1.js")
      val testJsPath = Paths.get("test.js")
      val failedJsMetricsToolExecRes =
        MetricsToolExecutorResult(Javascript.name, Set(test1JsPath, testJsPath), Failure(throwable))
      val test1JsFileMetrics =
        FileMetrics(test1JsPath, Some(13), None, None, None, None, Set(LineComplexity(37, 1)))
      val testJsFileMetrics =
        FileMetrics(testJsPath, Some(33), None, None, None, None, Set(LineComplexity(697, 2), LineComplexity(605, 3)))
      val jsResults = List(
        MetricsToolExecutorResult(
          Javascript.name,
          Set(test1JsPath, testJsPath),
          Success(Set(test1JsFileMetrics, testJsFileMetrics))),
        failedJsMetricsToolExecRes)

      val testPyPath = Paths.get("test.py")
      val testPyFileMetrics = FileMetrics(testPyPath, None, None, None, Some(0), None, Set())
      val failedPyMetricsToolExecRes =
        MetricsToolExecutorResult(Python.name, Set(testPyPath), Failure(throwable))
      val pythonResults = List(
        MetricsToolExecutorResult(Python.name, Set(testPyPath), Success(Set(testPyFileMetrics))),
        failedPyMetricsToolExecRes)

      val testRbPath = Paths.get("test.rb")
      val failedRbMetricsToolExecRes =
        MetricsToolExecutorResult(Ruby.name, Set(testRbPath), Failure(throwable))
      val testRbFileMetrics =
        FileMetrics(testRbPath, Some(48), None, None, None, None, Set(LineComplexity(97, 4), LineComplexity(82, 2)))
      val rubyResults = List(
        MetricsToolExecutorResult(Ruby.name, Set(testRbPath), Success(Set(testRbFileMetrics))),
        failedRbMetricsToolExecRes)

      val testCPath = Paths.get("test.c")
      val cResults = List(
        MetricsToolExecutorResult(
          C.name,
          Set(testCPath),
          Success(Set(FileMetrics(testCPath, None, Some(2), Some(3), None, None, Set())))),
        MetricsToolExecutorResult(C.name, Set(testCPath), Failure(throwable)))

      val testGoPath = Paths.get("test.go")
      val goResult = MetricsToolExecutorResult(Go.name, Set(testGoPath), Failure(throwable))

      val metrisToolExecutorResults = jsResults ++ pythonResults ++ rubyResults ++ cResults :+ goResult

      //When
      val resultsWithMissingMetrics =
        MetricsToolExecutor.calculateMissingFileMetrics(
          File("cli/src/test/resources/com/codacy/analysis/cli/samples"),
          metrisToolExecutorResults)

      //Then
      val expectedJsResults = List(
        MetricsToolExecutorResult(
          Javascript.name,
          Set(test1JsPath, testJsPath),
          Success(Set(test1JsFileMetrics.copy(loc = Some(43)), testJsFileMetrics.copy(loc = Some(1355))))),
        failedJsMetricsToolExecRes)

      val expectedPyResults = List(
        MetricsToolExecutorResult(Python.name, Set(testPyPath), Success(Set(testPyFileMetrics.copy(loc = Some(73))))),
        failedPyMetricsToolExecRes)

      val expectedRbResults = List(
        MetricsToolExecutorResult(Ruby.name, Set(testRbPath), Success(Set(testRbFileMetrics.copy(loc = Some(742))))),
        failedRbMetricsToolExecRes)

      val expectedGoResults = List(
        goResult,
        MetricsToolExecutorResult(
          Go.name,
          Set(testGoPath),
          Success(Set(FileMetrics(testGoPath, None, loc = Some(64), None, None, None, Set())))))

      resultsWithMissingMetrics must containTheSameElementsAs(
        expectedJsResults ++ expectedPyResults ++ expectedRbResults ++
          expectedGoResults ++ cResults)

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
