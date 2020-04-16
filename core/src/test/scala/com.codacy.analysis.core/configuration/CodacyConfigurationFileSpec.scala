package com.codacy.analysis.core.configuration

import better.files.Resource
import com.codacy.analysis.core.files.Glob
import com.codacy.plugins.api.languages.Languages
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification
import play.api.libs.json.Json

class CodacyConfigurationFileSpec extends Specification with NoLanguageFeatures {

  private val codacyConfigurationFileLoader = new CodacyConfigurationFileLoader()

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

      val resource = Resource.getAsString("com/codacy/analysis/core/configuration/codacy.yaml")

      val codacyConfigurationFile = codacyConfigurationFileLoader.parse(resource)

      codacyConfigurationFile must beEqualTo(Right(expected))
    }

    "be parseable" in {
      val moreFileContents = List(
        Resource.getAsString("com/codacy/analysis/core/configuration/codacy-test-1.yaml"),
        Resource.getAsString("com/codacy/analysis/core/configuration/codacy-test-2.yaml"),
        Resource.getAsString("com/codacy/analysis/core/configuration/codacy-test-3.yaml"))
        .map(codacyConfigurationFileLoader.parse)

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

      val resource =
        Resource.getAsString("com/codacy/analysis/core/configuration/codacy-langs.yaml")
      val codacyConfigurationFile = codacyConfigurationFileLoader.parse(resource)

      codacyConfigurationFile must beEqualTo(Right(expected))
    }
  }

}
