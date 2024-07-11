package com.codacy.analysis.core.tools

import com.codacy.analysis.core.files.FileCollector
import com.codacy.analysis.core.model
import com.codacy.analysis.core.ToolRepositoryMock
import com.codacy.analysis.core.utils.TestUtils._
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.api.duplication.DuplicationCloneFile
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.MatchResult
import org.specs2.mutable.Specification

import scala.util.Success

class DuplicationToolSpec extends Specification with NoLanguageFeatures {

  "DuplicationTool" should {
    val duplicationToolSpec =
      model.DuplicationToolSpec(dockerImage = "codacy/codacy-duplication-pmdcpd:2.2.2", Set(Languages.Javascript))

    "analyze duplication on a project" in {
      val commitUuid = "625e19cd9be4898939a7c40dbeb2b17e40df9d54"
      withClonedRepo("git@github.com:qamine-test/duplication-delta.git", commitUuid) { (_, directory) =>
        val expectedClones = Seq(
          model.DuplicationClone(
            "",
            165,
            18,
            Set(DuplicationCloneFile("test2.js", 13, 30), DuplicationCloneFile("test.js", 54, 71))),
          model.DuplicationClone(
            "",
            69,
            22,
            Set(DuplicationCloneFile("test.js", 1, 22), DuplicationCloneFile("test.js", 33, 54))))

        val result = for {
          fileTarget <- FileCollector.defaultCollector().list(directory)
          duplicationTool = new DuplicationTool(duplicationToolSpec, Languages.Javascript, "")
          duplicationToolResult <- duplicationTool.run(directory, fileTarget.readableFiles)
        } yield duplicationToolResult

        result must beSuccessfulTry
        result must beLike {
          case Success(duplicationResults) =>
            duplicationResults must haveSize(2)
            assertDuplication(duplicationResults, expectedClones)
        }
      }
    }

    "analyze duplication on a project, ignoring a file" in {
      val commitUuid = "625e19cd9be4898939a7c40dbeb2b17e40df9d54"
      withClonedRepo("git@github.com:qamine-test/duplication-delta.git", commitUuid) { (_, directory) =>
        val expectedClones = Seq(
          model.DuplicationClone(
            "",
            69,
            22,
            Set(DuplicationCloneFile("test.js", 1, 22), DuplicationCloneFile("test.js", 33, 54))))

        val result = for {
          fileTarget <- FileCollector.defaultCollector().list(directory)
          duplicationTool = new DuplicationTool(duplicationToolSpec, Languages.Javascript, "")
          filteredFileTarget = fileTarget.readableFiles.filterNot(_.endsWith("test2.js"))
          duplicationToolResult <- duplicationTool.run(directory, filteredFileTarget)
        } yield duplicationToolResult

        result must beSuccessfulTry
        result must beLike {
          case Success(duplicationResults) =>
            duplicationResults must haveSize(1)
            assertDuplication(duplicationResults, expectedClones)
        }
      }
    }
  }

  "DuplicationToolCollector" should {
    val languagesWithTools: Set[Language] = Set(Languages.Java, Languages.Python, Languages.Ruby)
    val duplicationToolCollector = new DuplicationToolCollector(ToolRepositoryMock)
    s"detect the duplication tools for the given languages: ${languagesWithTools.mkString(", ")}" in {
      val toolsEither = duplicationToolCollector.fromLanguages(languagesWithTools, "")
      toolsEither must beRight
      val tools = toolsEither.right.get
      tools must haveSize(3)
      tools.map(_.languageToRun) must containTheSameElementsAs(languagesWithTools.to[Seq])
    }

    val languagesWithoutTools: Set[Language] = Set(Languages.R, Languages.Elixir, Languages.Elm)

    s"return no duplication tools for the given languages: ${languagesWithoutTools}" in {
      val toolsEither = duplicationToolCollector.fromLanguages(languagesWithoutTools, "")
      toolsEither must beRight
      val tools = toolsEither.right.get
      tools should beEmpty
    }
  }

  def assertDuplication(duplicationResults: Set[model.DuplicationClone],
                        expectedClones: Seq[model.DuplicationClone]): MatchResult[Set[model.DuplicationClone]] = {
    //ignore the clone lines field for assertion
    duplicationResults.map(_.files.to[Set]) must containTheSameElementsAs(expectedClones.map(_.files.to[Set]))
    duplicationResults.map(_.copy(cloneLines = "", files = Set.empty)) must containTheSameElementsAs(
      expectedClones.map(_.copy(files = Set.empty)))
  }
}
