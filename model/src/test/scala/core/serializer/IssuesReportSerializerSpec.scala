package core.serializer

import java.nio.file.Paths

import com.codacy.analysis.core.model.{FullLocation, Issue, IssuesAnalysis, ToolResults}
import com.codacy.analysis.core.serializer.IssuesReportSerializer
import com.codacy.plugins.api.results.Pattern
import org.specs2.mutable.Specification
import org.specs2.control.NoLanguageFeatures

class IssuesReportSerializerSpec extends Specification with NoLanguageFeatures {
  "An IssuesAnalysis" should {
    val filePath = Paths.get("example")
    val toolName = "exampleTool"
    val patternId = "test"
    val patternMessage = "example message"
    val lineNumber = 1

    "be converted to json" in {
      val issuesReporterAsJson = IssuesReportSerializer.toJsonString(
        Set(ToolResults(
          toolName,
          IssuesAnalysis.Success(Set(IssuesAnalysis.FileResults(
            filePath,
            Set(Issue(
              Pattern.Id(patternId),
              filePath,
              Issue.Message(patternMessage),
              com.codacy.plugins.api.results.Result.Level.Info,
              None,
              FullLocation(lineNumber, lineNumber)))))))))

      val expectedJSON =
        s"""[{"tool":"$toolName","issues":{"Success":{"results":[{"filename":"${filePath.toString}","results":[{"Issue":{"patternId":{"value":"$patternId"},"filename":"${filePath.toString}","message":{"text":"$patternMessage"},"level":"Info","location":{"FullLocation":{"line":$lineNumber,"column":$lineNumber}}}}]}]}}}]"""

      expectedJSON mustEqual issuesReporterAsJson
    }

    "have category in generated json" in {
      val issuesReporterAsJson = IssuesReportSerializer.toJsonString(
        Set(ToolResults(
          toolName,
          IssuesAnalysis.Success(Set(IssuesAnalysis.FileResults(
            filePath,
            Set(Issue(
              Pattern.Id(patternId),
              filePath,
              Issue.Message(patternMessage),
              com.codacy.plugins.api.results.Result.Level.Info,
              Some(com.codacy.plugins.api.results.Pattern.Category.UnusedCode),
              FullLocation(lineNumber, lineNumber)))))))))

      val expectedJSON =
        s"""[{"tool":"$toolName","issues":{"Success":{"results":[{"filename":"${filePath.toString}","results":[{"Issue":{"patternId":{"value":"$patternId"},"filename":"${filePath.toString}","message":{"text":"$patternMessage"},"level":"Info","category":"UnusedCode","location":{"FullLocation":{"line":$lineNumber,"column":$lineNumber}}}}]}]}}}]"""

      expectedJSON mustEqual issuesReporterAsJson
    }

    "return failure json" in {
      val errorMsg = "This is a failure"
      val failureJson =
        IssuesReportSerializer.toJsonString(Set(ToolResults(toolName, IssuesAnalysis.Failure(errorMsg))))

      val expectedJSON = s"""[{"tool":"$toolName","issues":{"Failure":{"message":"$errorMsg"}}}]"""

      expectedJSON mustEqual failureJson
    }
  }
}
