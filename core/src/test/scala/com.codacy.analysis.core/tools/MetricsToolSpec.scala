package com.codacy.analysis.core.tools

import java.nio.file.Paths

import com.codacy.analysis.core.model.FileMetrics
import com.codacy.analysis.core.utils.TestUtils._
import com.codacy.plugins.api.Source
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.metrics.docker.Cloc
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

import scala.util.Success

class MetricsToolSpec extends Specification with NoLanguageFeatures {

  val jsTest2Metrics =
    FileMetrics(Paths.get("test2.js"), None, Some(25), Some(0), None, None, Set())
  val jsTestMetrics = FileMetrics(Paths.get("test.js"), None, Some(60), Some(0), None, None, Set())

  "MetricsTool" should {
    "analyze metrics on a project" in {
      val commitUuid = "625e19cd9be4898939a7c40dbeb2b17e40df9d54"
      withClonedRepo("git://github.com/qamine-test/duplication-delta.git", commitUuid) { (_, directory) =>
        val testProjectFileMetrics = List(jsTest2Metrics, jsTestMetrics)

        val metricsTool = new MetricsTool(Cloc, Languages.Javascript)

        val result = metricsTool.run(directory, None)

        result must beSuccessfulTry
        result must beLike {
          case Success(metricsResults) =>
            metricsResults must haveSize(testProjectFileMetrics.size)
            metricsResults must containTheSameElementsAs(testProjectFileMetrics)
        }
      }
    }

    "analyze metrics on a project, ignoring a file" in {
      val commitUuid = "625e19cd9be4898939a7c40dbeb2b17e40df9d54"
      withClonedRepo("git://github.com/qamine-test/duplication-delta.git", commitUuid) { (_, directory) =>
        val testProjectFileMetrics = List(jsTestMetrics)

        val metricsTool = new MetricsTool(Cloc, Languages.Javascript)

        val result = metricsTool.run(directory, Some(Set(Source.File("test.js"))))

        result must beSuccessfulTry
        result must beLike {
          case Success(metricsResults) =>
            metricsResults must haveSize(testProjectFileMetrics.size)
            metricsResults must containTheSameElementsAs(testProjectFileMetrics)
        }
      }
    }
  }

  "MetricsToolCollector" should {
    val languagesWithTools: Set[Language] = Set(Languages.Kotlin, Languages.Go, Languages.LESS)
    s"detect the metrics tools for the given languages: ${languagesWithTools.mkString(", ")}" in {

      val tools = MetricsToolCollector.fromLanguages(languagesWithTools)

      tools must haveSize(5)

      tools.map(_.languageToRun) must containTheSameElementsAs(languagesWithTools.to[Seq])
    }
  }
}
