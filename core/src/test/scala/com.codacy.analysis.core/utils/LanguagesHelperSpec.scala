package com.codacy.analysis.core.utils

import better.files.File
import com.codacy.analysis.core.files.FilesTarget
import com.codacy.plugins.api.languages.Languages
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

class LanguagesHelperSpec extends Specification with NoLanguageFeatures {

  "LanguagesHelper" should {
    "detect the languages from a given set of files" in {

      val filesTarget =
        FilesTarget(File(""), Set(File("test.js").path, File("Test.java").path, File("SomeClazz.rb").path), Set.empty)

      val languages = LanguagesHelper.fromFileTarget(filesTarget, Map.empty)

      languages should containTheSameElementsAs(Seq(Languages.Java, Languages.Ruby, Languages.Javascript))
    }

    "detect the languages from a given set of files, considering custom extensions for some of them" in {
      val filesTarget = FilesTarget(
        File(""),
        Set(
          File("test.css").path,
          File("test.js-that_will_be_kotlin").path,
          File("Test-1.java-that_will_be_ruby").path,
          File("test-rb.resource").path),
        Set.empty)

      val languages = LanguagesHelper.fromFileTarget(
        filesTarget,
        Map(
          Languages.Ruby -> Set("-rb.resource", "-1.java-that_will_be_ruby"),
          Languages.Kotlin -> Set(".js-that_will_be_kotlin")))

      languages should containTheSameElementsAs(Seq(Languages.Kotlin, Languages.Ruby, Languages.CSS))
    }

    "return an empty set of languages for extensions that do not match any language" in {
      val filesTarget =
        FilesTarget(
          File(""),
          Set(File("test.exotericLanguage").path, File("test-rb.anotherLanguageThatNoOneUses").path),
          Set.empty)

      val languages = LanguagesHelper.fromFileTarget(filesTarget, Map.empty)

      languages should beEmpty
    }
  }
}
