package core.serializer

import java.io.File

import com.codacy.analysis.core.model.{FullLocation, Issue, IssuesAnalysis, ToolResults}
import com.codacy.analysis.core.serializer.IssuesReportSerializer
import com.codacy.plugins.api.results.Pattern
import org.specs2.mutable.Specification
import org.specs2.control.NoLanguageFeatures

class IssuesReportSerializerSpec extends Specification with NoLanguageFeatures {
  "An IssuesAnalysis" should {
    "be converted to json" in {
      val filePath = new File("example").getAbsoluteFile.toPath

      val toolName = "exampleTool"
      val patternId = "test"
      val patternMessage = "example message"
      val lineNumber = 1

      val issuesReporterAsJson = IssuesReportSerializer.toJsonString(
        Set(
          ToolResults(
            toolName,
            IssuesAnalysis.Success(
              Set(
                IssuesAnalysis.FileResults(
                  filePath,
                  Set(
                    Issue(
                      Pattern.Id(patternId),
                      filePath,
                      Issue.Message(patternMessage),
                      com.codacy.plugins.api.results.Result.Level.Info,
                      None,
                      FullLocation(lineNumber, lineNumber)
                    )
                  )
                )
              )
            )
          )
        )
      )

      val expectedJSON =
        s"""[{"tool":"$toolName","issues":{"Success":{"results":[{"filename":"${filePath.toString}","results":[{"Issue":{"patternId":{"value":"$patternId"},"filename":"${filePath.toString}","message":{"text":"$patternMessage"},"level":"Info","location":{"FullLocation":{"line":$lineNumber,"column":$lineNumber}}}}]}]}}}]"""

      expectedJSON mustEqual issuesReporterAsJson
    }
  }
}
