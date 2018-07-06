package com.codacy.analysis.core.tools

import better.files.File
import com.codacy.analysis.core.files.{FileCollector, FilesTarget}
import com.codacy.analysis.core.model.DuplicationClone
import com.codacy.analysis.core.utils.TestUtils._
import com.codacy.plugins.api.languages.Languages
import com.codacy.plugins.duplication.api.DuplicationCloneFile
import com.codacy.plugins.duplication.docker.pmdcpd.PmdCpd
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

import scala.util.Success

class DuplicationToolSpec extends Specification with NoLanguageFeatures {

  "DuplicationTool" should {
    "analyse duplication on a project" in {
      val commitUuid = "625e19cd9be4898939a7c40dbeb2b17e40df9d54"
      withClonedRepo("git://github.com/qamine-test/duplication-delta.git", commitUuid) { (_, directory) =>
        val expectedClones = Seq(
          DuplicationClone(
            "",
            165,
            18,
            List(DuplicationCloneFile("test2.js", 13, 30), DuplicationCloneFile("test.js", 54, 71))),
          DuplicationClone(
            "",
            69,
            22,
            List(DuplicationCloneFile("test.js", 1, 22), DuplicationCloneFile("test.js", 33, 54))))

        val result = for {
          fileTarget <- FileCollector.defaultCollector().list(directory, Left("not needed"), Left("not needed"))
          duplicationTool = new DuplicationTool(PmdCpd, Languages.Javascript)
          duplicationToolResult <- duplicationTool.run(directory, fileTarget)
        } yield duplicationToolResult

        result must beSuccessfulTry
        result must beLike {
          case Success(duplicationResults) =>
            duplicationResults must haveSize(2)
            //ignore the clone lines field for assertion
            duplicationResults.map(_.copy(cloneLines = "")) must containTheSameElementsAs(expectedClones)
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
            List(DuplicationCloneFile("test.js", 1, 22), DuplicationCloneFile("test.js", 33, 54))))

        val result = for {
          fileTarget <- FileCollector.defaultCollector().list(directory, Left("not needed"), Left("not needed"))
          duplicationTool = new DuplicationTool(PmdCpd, Languages.Javascript)
          filteredFileTarget = fileTarget.readableFiles.filterNot(_.endsWith("test2.js"))
          duplicationToolResult <- duplicationTool.run(directory, fileTarget.copy(readableFiles = filteredFileTarget))
        } yield duplicationToolResult

        result must beSuccessfulTry
        result must beLike {
          case Success(duplicationResults) =>
            duplicationResults must haveSize(1)
            //ignore the clone lines field for assertion
            duplicationResults.map(_.copy(cloneLines = "")) must containTheSameElementsAs(expectedClones)
        }
      }
    }
  }

  "DuplicationToolCollector" should {
    "detect the duplication tools to be used from the project files" in {

      val filesTarget = FilesTarget(File(""), Set(File("Test.java").path, File("SomeClazz.rb").path), Set.empty)

      val toolEither = DuplicationToolCollector.fromFileTarget(filesTarget, List.empty)

      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.map(_.name) mustEqual Set("duplication")
          toolSet.map(_.language) mustEqual Set(Languages.Java, Languages.Ruby)
      }
    }

    "detect the duplication tools to be used from the project files, considering custom extensions" in {

      val filesTarget =
        FilesTarget(File(""), Set(File("test-rb.resource").path), Set.empty)

      val toolEither =
        DuplicationToolCollector.fromFileTarget(filesTarget, List(Languages.Ruby -> List("-rb.resource")))

      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.map(_.name) mustEqual Set("duplication")
          toolSet.map(_.language) mustEqual Set(Languages.Ruby)
      }
    }

    "detect the duplication tool from a given language" in {

      val toolEither = DuplicationToolCollector.fromLanguage(Languages.Ruby.name)

      toolEither must beRight
      toolEither must beLike {
        case Right(toolSet) =>
          toolSet.map(_.name) mustEqual Set("duplication")
          toolSet.map(_.language) mustEqual Set(Languages.Ruby)
      }
    }
  }
}
