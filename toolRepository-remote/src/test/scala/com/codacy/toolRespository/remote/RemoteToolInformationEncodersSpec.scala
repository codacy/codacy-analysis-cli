package com.codacy.toolRespository.remote

import com.codacy.analysis.core.model.{PatternSpec, ToolSpec}
import com.codacy.plugins.api.languages.Language
import com.codacy.plugins.api.languages.Languages.Python
import com.codacy.toolRepository.remote.RemoteToolInformationEncoders._
import io.circe._
import io.circe.syntax._
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class RemoteToolInformationEncodersSpec
    extends Specification
    with NoLanguageFeatures
    with Mockito
    with FutureMatchers {

  def toolSpec(languages: Set[Language] = Set(Python)) =
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
      isClientSide = false,
      hasUIConfiguration = true)

  def toolPatternsSpec(subcategory: Option[String] = None) =
    PatternSpec(
      id = "test",
      level = "Info",
      category = "Security",
      subCategory = subcategory,
      title = "Test title",
      description = Some(""),
      explanation = Some(""),
      enabled = true,
      timeToFix = Some(5),
      parameters = Seq.empty,
      languages = Set(Python))

  def toolSpecAsJson(language: String = "Python") = s"""{
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
                         |  "isClientSide" : false,
                         |  "hasUIConfiguration" : true
                         |}""".stripMargin

  "ToolCacheSpec" should {
    s"Encode tool spec correctly".stripMargin in {

      val toolSpecJson = toolSpec().asJson
      toolSpecJson.toString shouldEqual toolSpecAsJson()
    }

    s"Decode tool spec correctly".stripMargin in {

      val toolSpecDecoded = parser.decode[ToolSpec](toolSpecAsJson())
      toolSpecDecoded shouldEqual Right(toolSpec())
    }

    s"Decode tool spec with languages None".stripMargin in {

      val toolSpecDecoded = parser.decode[ToolSpec](toolSpecAsJson("NotFound"))
      toolSpecDecoded shouldEqual Right(toolSpec(Set.empty))
    }
  }
}
