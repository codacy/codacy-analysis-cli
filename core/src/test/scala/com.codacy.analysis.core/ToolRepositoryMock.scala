package com.codacy.analysis.core

import com.codacy.analysis.core.model.{AnalyserError, PatternSpec, ToolSpec}
import com.codacy.analysis.core.tools.ToolRepository
import com.codacy.plugins.api.languages.Languages

/**
  * This class can be used to replace calls to codacy API related to tools.
  * It's just a placeholder until we find a better way to introduce mocks and
  * do integration tests.
  */
class ToolRepositoryMock extends ToolRepository {

  private val pylintId = "34225275-f79e-4b85-8126-c7512c987c0d"
  private val eslintId = "cf05f3aa-fd23-4586-8cce-5368917ec3e5"
  private val csslintId = "997201eb-0907-4823-87c0-a8f7703531e7"
  private val brakemanId = "c6273c22-5248-11e5-885d-feff819cdc9f"
  private val scalastyleId = "21586cd3-3eaa-4454-878e-ac0211a833c2"

  override def list(): Either[AnalyserError, Seq[ToolSpec]] =
    Right(
      Seq(
        ToolSpec(
          uuid = pylintId,
          dockerImage = "codacy/codacy-pylint:3.2.0",
          isDefault = true,
          version = "",
          languages = Set(Languages.Python),
          name = "PyLint",
          shortName = "pylint",
          documentationUrl = None,
          sourceCodeUrl = None,
          prefix = "PyLint_",
          needsCompilation = false,
          hasConfigFile = true,
          configFilenames = Set.empty,
          isClientSide = false,
          hasUIConfiguration = true),
        ToolSpec(
          uuid = eslintId,
          dockerImage = "codacy/codacy-eslint:5.9.4",
          isDefault = true,
          version = "",
          languages = Set(Languages.Javascript, Languages.JSON, Languages.TypeScript),
          name = "ESLint",
          shortName = "eslint",
          documentationUrl = None,
          sourceCodeUrl = None,
          prefix = "ESLint_",
          needsCompilation = false,
          hasConfigFile = true,
          configFilenames = Set.empty,
          isClientSide = false,
          hasUIConfiguration = true),
        ToolSpec(
          uuid = csslintId,
          dockerImage = "codacy/codacy-csslint:3.2.1",
          isDefault = true,
          version = "",
          languages = Set(Languages.CSS),
          name = "CSSLint",
          shortName = "csslint",
          documentationUrl = None,
          sourceCodeUrl = None,
          prefix = "CSSLint_",
          needsCompilation = false,
          hasConfigFile = true,
          configFilenames = Set.empty,
          isClientSide = false,
          hasUIConfiguration = true),
        ToolSpec(
          uuid = brakemanId,
          dockerImage = "codacy/codacy-brakeman:1.3.1",
          isDefault = true,
          version = "",
          languages = Set(Languages.Ruby),
          name = "Brakeman",
          shortName = "brakeman",
          documentationUrl = None,
          sourceCodeUrl = None,
          prefix = "",
          needsCompilation = false,
          hasConfigFile = true,
          configFilenames = Set("config/brakeman.yml"),
          isClientSide = false,
          hasUIConfiguration = true),
        ToolSpec(
          uuid = scalastyleId,
          dockerImage = "codacy/codacy-scalastyle:latest",
          isDefault = true,
          version = "",
          languages = Set(Languages.Scala),
          name = "ScalaStyle",
          shortName = "scalastyle",
          documentationUrl = None,
          sourceCodeUrl = None,
          prefix = "ScalaStyle_",
          needsCompilation = false,
          hasConfigFile = true,
          configFilenames = Set("scalastyle-config.xml", "scalastyle_config.xml"),
          isClientSide = false,
          hasUIConfiguration = true)))

  override def get(uuid: String): Either[AnalyserError, ToolSpec] =
    list().flatMap(_.find(_.uuid == uuid).toRight(AnalyserError.FailedToFindTool(uuid)))

  private def getSimplePattern(patternId: String): PatternSpec =
    PatternSpec(patternId, "Info", "ErrorProne", None, patternId, None, None, enabled = true, None, Seq(), Set())

  override def listPatterns(tool: ToolSpec): Either[AnalyserError, Seq[PatternSpec]] =
    Map(
      pylintId -> Seq("PyLint_C0111", "PyLint_E1101").map(getSimplePattern),
      eslintId -> Seq("ESLint_semi", "ESLint_no-undef", "ESLint_indent", "ESLint_no-empty").map(getSimplePattern),
      csslintId -> Seq("CSSLint_important").map(getSimplePattern),
      brakemanId -> Seq().map(getSimplePattern),
      scalastyleId -> Seq().map(getSimplePattern))
      .get(tool.uuid)
      .toRight(AnalyserError.FailedToListPatterns(tool.uuid, ""))
}
