package com.codacy.toolRespository.remote

import com.codacy.analysis.core.model.{PatternSpec, ToolSpec}
import com.codacy.plugins.api.languages.Language
import com.codacy.plugins.api.languages.Languages.Python
import com.codacy.toolRepository.remote.storage.ToolPatternsSpecsEncoders._
import io.circe._
import io.circe.syntax._
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class ToolPatternsSpecsEncodersSpec extends Specification with NoLanguageFeatures with Mockito with FutureMatchers {

  def toolSpec(languages: Set[Language] = Set(Python)): ToolSpec =
    ToolSpec(
      uuid = "34225275-f79e-4b85-8126-c7512c987c0d",
      dockerImage = "codacy/codacy-example-tool:1.0.0",
      isDefault = true,
      version = "",
      languages = languages,
      name = "PyLint",
      shortName = "PyLint",
      documentationUrl = None,
      sourceCodeUrl = None,
      prefix = "",
      needsCompilation = false,
      hasConfigFile = true,
      configFilenames = Set.empty,
      standalone = false,
      hasUIConfiguration = true)

  def toolSpecAsJson(language: String = "Python"): String = s"""{
                                                               |  "uuid" : "34225275-f79e-4b85-8126-c7512c987c0d",
                                                               |  "dockerImage" : "codacy/codacy-example-tool:1.0.0",
                                                               |  "isDefault" : true,
                                                               |  "version" : "",
                                                               |  "languages" : [
                                                               |    {
                                                               |      "name" : "${language}"
                                                               |    }
                                                               |  ],
                                                               |  "name" : "PyLint",
                                                               |  "shortName" : "PyLint",
                                                               |  "documentationUrl" : null,
                                                               |  "sourceCodeUrl" : null,
                                                               |  "prefix" : "",
                                                               |  "needsCompilation" : false,
                                                               |  "hasConfigFile" : true,
                                                               |  "configFilenames" : [
                                                               |  ],
                                                               |  "standalone" : false,
                                                               |  "hasUIConfiguration" : true
                                                               |}""".stripMargin

  val patternSpec: PatternSpec =
    PatternSpec(
      id = "test",
      level = "Info",
      category = "Security",
      subCategory = None,
      title = "Test title",
      description = Some(""),
      explanation = Some(""),
      enabled = true,
      timeToFix = Some(5),
      parameters = Seq.empty,
      languages = Set(Python))

  val toolPatternSpecJson: String = s"""{
                                       |  "id" : "test",
                                       |  "level" : "Info",
                                       |  "category" : "Security",
                                       |  "subCategory" : null,
                                       |  "title" : "Test title",
                                       |  "description" : "",
                                       |  "explanation" : "",
                                       |  "enabled" : true,
                                       |  "timeToFix" : 5,
                                       |  "parameters" : [
                                       |  ],
                                       |  "languages" : [
                                       |    {
                                       |      "name" : "Python"
                                       |    }
                                       |  ]
                                       |}""".stripMargin

  "ToolPatternsSpecsEncoders" should {
    "Encode tool spec correctly".stripMargin in {
      toolSpec().asJson.toString shouldEqual toolSpecAsJson()
    }

    "Decode tool spec correctly".stripMargin in {

      val toolSpecDecoded = parser.decode[ToolSpec](toolSpecAsJson())
      toolSpecDecoded shouldEqual Right(toolSpec())
    }

    "Decode tool spec with languages None".stripMargin in {

      val toolSpecDecoded = parser.decode[ToolSpec](toolSpecAsJson("NotFound"))
      toolSpecDecoded shouldEqual Right(toolSpec(Set.empty))
    }

    "Encode pattern spec correctly".stripMargin in {
      patternSpec.asJson.toString shouldEqual toolPatternSpecJson
    }

    "Decode pattern spec correctly".stripMargin in {
      val decodedPatternSpec = parser.decode[PatternSpec](toolPatternSpecJson)
      decodedPatternSpec shouldEqual Right(patternSpec)
    }
  }
}
