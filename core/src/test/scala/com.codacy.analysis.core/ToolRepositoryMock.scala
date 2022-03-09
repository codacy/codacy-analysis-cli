package com.codacy.analysis.core

import com.codacy.analysis.core.model.{AnalyserError, PatternSpec, ToolSpec}
import com.codacy.analysis.core.tools.ToolRepository
import com.codacy.plugins.api.languages.Languages
import com.codacy.analysis.core.model.DuplicationToolSpec
import com.codacy.analysis.core.model.MetricsToolSpec

/**
  * This object can be used to replace calls to codacy API related to tools.
  * It's just a placeholder until we find a better way to introduce mocks and
  * do integration tests.
  */
object ToolRepositoryMock extends ToolRepository {

  private val pylintId = "34225275-f79e-4b85-8126-c7512c987c0d"
  private val eslintId = "cf05f3aa-fd23-4586-8cce-5368917ec3e5"
  private val csslintId = "997201eb-0907-4823-87c0-a8f7703531e7"
  private val brakemanId = "c6273c22-5248-11e5-885d-feff819cdc9f"
  private val scalastyleId = "21586cd3-3eaa-4454-878e-ac0211a833c2"

  override val allTools: Either[AnalyserError, Seq[ToolSpec]] =
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
          standalone = false,
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
          standalone = false,
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
          standalone = false,
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
          standalone = false,
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
          standalone = false,
          hasUIConfiguration = true)))

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

  override def listDuplicationTools(): Either[AnalyserError, Seq[DuplicationToolSpec]] =
    Right(
      Seq(
        DuplicationToolSpec(
          "codacy/codacy-duplication-pmdcpd:2.2.2",
          Set(
            Languages.CPP,
            Languages.Scala,
            Languages.Go,
            Languages.Python,
            Languages.Javascript,
            Languages.C,
            Languages.CSharp,
            Languages.Java,
            Languages.Swift)),
        DuplicationToolSpec("codacy/codacy-duplication-phpcpd:2.1.4", Set(Languages.PHP)),
        DuplicationToolSpec("codacy/codacy-duplication-flay:2.1.4", Set(Languages.Ruby)),
        DuplicationToolSpec("codacy/codacy-duplication-jscpd:0.1.6", Set(Languages.Kotlin, Languages.TypeScript))))

  override def listMetricsTools(): Either[AnalyserError, Seq[MetricsToolSpec]] =
    Right(
      Seq(
        MetricsToolSpec(dockerImage = "codacy/codacy-metrics-pmd:0.2.4", languages = Set(Languages.Java)),
        MetricsToolSpec(dockerImage = "codacy/codacy-metrics-gocyclo:0.2.4", languages = Set(Languages.Go)),
        MetricsToolSpec(dockerImage = "codacy/codacy-metrics-scalastyle:0.2.4", languages = Set(Languages.Scala)),
        MetricsToolSpec(dockerImage = "codacy/codacy-metrics-radon:0.2.4", languages = Set(Languages.Python)),
        MetricsToolSpec(
          dockerImage = "codacy/codacy-metrics-eslint:1.1.250",
          languages = Set(Languages.Javascript, Languages.TypeScript)),
        MetricsToolSpec(dockerImage = "codacy/codacy-metrics-pdepend:0.5.0", languages = Set(Languages.PHP)),
        MetricsToolSpec(dockerImage = "codacy/codacy-metrics-rubocop:0.4.1", languages = Set(Languages.Ruby)),
        MetricsToolSpec(dockerImage = "codacy/codacy-metrics-scala:0.2.4", languages = Set(Languages.Scala)),
        MetricsToolSpec(dockerImage = "codacy/codacy-metrics-sonar-csharp:0.1.36", languages = Set(Languages.CSharp)),
        MetricsToolSpec(dockerImage = "codacy/codacy-metrics-sonar-csharp:0.1.36", languages = Set(Languages.CSharp)),
        MetricsToolSpec(dockerImage = "codacy/codacy-metrics-detekt:0.1.2", languages = Set(Languages.Kotlin)),
        MetricsToolSpec(dockerImage = "codacy/codacy-metrics-cloc:0.4.1", languages = Languages.all)))
}
