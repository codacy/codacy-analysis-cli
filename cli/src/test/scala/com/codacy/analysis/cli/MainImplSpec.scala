package com.codacy.analysis.cli

import caseapp.Tag
import com.codacy.analysis.cli.analysis.ExitStatus
import com.codacy.analysis.cli.command._
import com.codacy.analysis.core.utils.TestUtils._
import com.codacy.analysis.core.clients.{ProjectName, UserName}
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

import scala.concurrent.duration._

class MainImplSpec extends Specification with NoLanguageFeatures {

  private val cli = new MainImpl() {
    override def exit(code: Int): Unit = ()
  }

  "MainImpl" should {

    "timeout analysis after 1 second" in {
      withClonedRepo("git://github.com/qamine-test/codacy-brakeman", "266c56a61d236ed6ee5efa58c0e621125498dd5f") {
        (file, directory) =>
          cli.runCommand(
            Analyse(
              options = CommonOptions(verbose = Tag.of(1)),
              api = APIOptions(
                projectToken = Option.empty[String],
                apiToken = Option.empty[String],
                username = Option.empty[UserName],
                project = Option.empty[ProjectName],
                codacyApiBaseUrl = Option.empty[String]),
              tool = Option("brakeman"),
              directory = Option(directory),
              output = Option(file),
              failIfIncomplete = Tag.of(1),
              toolTimeout = Option(1.nanosecond),
              extras = ExtraOptions())) must beEqualTo(ExitStatus.ExitCodes.partiallyFailedAnalysis)
      }
    }

  }

}
