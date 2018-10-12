package com.codacy.analysis.core.tools

import com.codacy.analysis.core.files.FileCollector
import com.codacy.analysis.core.model.DuplicationClone
import com.codacy.analysis.core.utils.TestUtils._
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.duplication.api.DuplicationCloneFile
import com.codacy.plugins.duplication.docker.PmdCpd
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.MatchResult
import org.specs2.mutable.Specification
import scalaz.zio.RTS

import scala.util.{Success, Try}

class DuplicationToolSpec extends Specification with NoLanguageFeatures with RTS {

  "DuplicationTool" should {
    "analyse duplication on a project" in {
      val commitUuid = "625e19cd9be4898939a7c40dbeb2b17e40df9d54"
      withClonedRepo("git://github.com/qamine-test/duplication-delta.git", commitUuid) { (_, directory) =>
        val expectedClones = Seq(
          DuplicationClone(
            "",
            165,
            18,
            Set(DuplicationCloneFile("test2.js", 13, 30), DuplicationCloneFile("test.js", 54, 71))),
          DuplicationClone(
            "",
            69,
            22,
            Set(DuplicationCloneFile("test.js", 1, 22), DuplicationCloneFile("test.js", 33, 54))))

        val result = Try(unsafeRun(for {
          fileTarget <- FileCollector.defaultCollector().list(directory)
          duplicationTool = new DuplicationTool(PmdCpd, Languages.Javascript)
          duplicationToolResult <- duplicationTool.run(directory, fileTarget.readableFiles)
        } yield duplicationToolResult))

        result must beSuccessfulTry
        result must beLike {
          case Success(duplicationResults) =>
            duplicationResults must haveSize(2)
            assertDuplication(duplicationResults, expectedClones)
        }
      }
    }

    "analyse duplication on a project, ignoring a file" in {
      val commitUuid = "625e19cd9be4898939a7c40dbeb2b17e40df9d54"
      withClonedRepo("git://github.com/qamine-test/duplication-delta.git", commitUuid) { (_, directory) =>
        val expectedClones = Seq(
          DuplicationClone(
            "",
            69,
            22,
            Set(DuplicationCloneFile("test.js", 1, 22), DuplicationCloneFile("test.js", 33, 54))))

        val result = for {
          fileTarget <- FileCollector.defaultCollector().list(directory)
          duplicationTool = new DuplicationTool(PmdCpd, Languages.Javascript)
          filteredFileTarget = fileTarget.readableFiles.filterNot(_.endsWith("test2.js"))
          duplicationToolResult <- duplicationTool.run(directory, filteredFileTarget)
        } yield duplicationToolResult

        Try(unsafeRun(result)) must beSuccessfulTry
        Try(unsafeRun(result)) must beLike {
          case Success(duplicationResults) =>
            duplicationResults must haveSize(1)
            assertDuplication(duplicationResults, expectedClones)
        }
      }
    }
  }

  "DuplicationToolCollector" should {
    val languagesWithTools: Set[Language] = Set(Languages.Java, Languages.Python, Languages.Ruby)
    s"detect the duplication tools for the given languages: ${languagesWithTools.mkString(", ")}" in {

      val tools = DuplicationToolCollector.fromLanguages(languagesWithTools)

      tools must haveSize(3)
      tools.map(_.languageToRun) must containTheSameElementsAs(languagesWithTools.to[Seq])
    }

    val languagesWithoutTools: Set[Language] = Set(Languages.R, Languages.Elixir, Languages.Elm)

    s"return no duplication tools for the given languages: ${languagesWithoutTools}" in {

      val tools = DuplicationToolCollector.fromLanguages(languagesWithoutTools)

      tools should beEmpty
    }
  }

  def assertDuplication(duplicationResults: Set[DuplicationClone],
                        expectedClones: Seq[DuplicationClone]): MatchResult[Set[DuplicationClone]] = {
    //ignore the clone lines field for assertion
    duplicationResults.map(_.files.to[Set]) must containTheSameElementsAs(expectedClones.map(_.files.to[Set]))
    duplicationResults.map(_.copy(cloneLines = "", files = Set.empty)) must containTheSameElementsAs(
      expectedClones.map(_.copy(files = Set.empty)))
  }
}
