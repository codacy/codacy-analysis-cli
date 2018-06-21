package com.codacy.analysis.cli.configuration

import better.files.File
import com.codacy.analysis.cli.files.Glob
import com.codacy.plugins.api.languages.Languages
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification
import play.api.libs.json.Json

class CodacyConfigurationFileSpec extends Specification with NoLanguageFeatures {

  "CodacyConfigurationFile" should {
    "be parsed as expected" in {
      val expected = CodacyConfigurationFile(
        Option(
          Map(
            "rubocop" -> EngineConfiguration(Some(Set(Glob("config/engines.yml"))), Some("test/baseDir"), None),
            "duplication" -> EngineConfiguration(
              Some(Set(Glob("config/engines.yml"))),
              None,
              Some(Map(("config", Json.obj(("languages", Json.arr("ruby"))))))),
            "metrics" -> EngineConfiguration(Some(Set(Glob("config/engines.yml"))), None, None),
            "coverage" -> EngineConfiguration(Some(Set(Glob("config/engines.yml"))), None, None))),
        Option(Set(Glob(".bundle/"), Glob("spec/**/*"), Glob("benchmarks/**/*"))),
        None)

      val resource = File.resource("com/codacy/analysis/cli/configuration/codacy.yaml")
      val codacyConfigurationFile = CodacyConfigurationFile.load(resource)

      codacyConfigurationFile must beEqualTo(Right(expected))
    }

    "be parseable" in {
      val moreFileContents = List(
        File.resource("com/codacy/analysis/cli/configuration/codacy-test-1.yaml"),
        File.resource("com/codacy/analysis/cli/configuration/codacy-test-2.yaml"),
        File.resource("com/codacy/analysis/cli/configuration/codacy-test-3.yaml")).map(CodacyConfigurationFile.load)

      foreach(moreFileContents)(_ must beRight)
    }

    "be parsed with language configs" in {
      val expected = CodacyConfigurationFile(
        Option(
          Map(
            "rubocop" -> EngineConfiguration(Some(Set(Glob("config/engines.yml"))), Some("test/baseDir"), None),
            "duplication" -> EngineConfiguration(
              Some(Set(Glob("config/engines.yml"))),
              None,
              Some(Map(("config", Json.obj(("languages", Json.arr("ruby"))))))),
            "metrics" -> EngineConfiguration(Some(Set(Glob("config/engines.yml"))), None, None),
            "coverage" -> EngineConfiguration(Some(Set(Glob("config/engines.yml"))), None, None))),
        Option(Set(Glob(".bundle/"), Glob("spec/**/*"), Glob("benchmarks/**/*"))),
        Some(Map((Languages.CSS, LanguageConfiguration(Some(Set("-css.resource")))))))

      val resource = File.resource("com/codacy/analysis/cli/configuration/codacy-langs.yaml")
      val codacyConfigurationFile = CodacyConfigurationFile.load(resource)

      codacyConfigurationFile must beEqualTo(Right(expected))
    }
  }

}
