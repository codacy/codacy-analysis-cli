package com.codacy.toolRespository.remote

import com.codacy.analysis.core.model.ToolSpec
import com.codacy.plugins.api.languages.Languages
import com.codacy.toolRepository.remote.{RemoteToolInformation, RemoteToolsDataStorage}
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class RemoteToolsDataStorageSpec extends Specification with NoLanguageFeatures with Mockito with FutureMatchers {

  val toolVersion = "1.0.0"

  def toolSpec(uuid: String, version: String = toolVersion): ToolSpec =
    ToolSpec(
      uuid = uuid,
      "codacy:1.0.0",
      isDefault = false,
      version,
      Set(Languages.Python),
      "name",
      "shortName",
      None,
      None,
      "",
      needsCompilation = false,
      hasConfigFile = false,
      Set.empty,
      isClientSide = false,
      hasUIConfiguration = false)

  val storage = new RemoteToolsDataStorage

  "RemoteToolsDataStorage" should {

    s"add list of tools".stripMargin in {
      val currentList = Seq.empty
      val newListOfAllTools = Seq(toolSpec("uuid"), toolSpec("uuid 2"))
      val expectedListOfRemoteTools =
        Seq(RemoteToolInformation(toolSpec("uuid"), None), RemoteToolInformation(toolSpec("uuid 2"), None))

      val res = storage.mergeToolsList(currentList, newListOfAllTools)
      res shouldEqual expectedListOfRemoteTools
    }

    s"not remove list of patterns already present in tool when new list contains tool".stripMargin in {
      val currentListOfRemoteToolInfo =
        Seq(RemoteToolInformation(toolSpec("uuid"), None), RemoteToolInformation(toolSpec("uuid 2"), Some(Seq.empty)))
      val newListOfAllTools = Seq(toolSpec("uuid"), toolSpec("uuid 2"))

      val res = storage.mergeToolsList(currentListOfRemoteToolInfo, newListOfAllTools)
      res shouldEqual currentListOfRemoteToolInfo
    }

    s"remove tool that is not present in new list contains tool".stripMargin in {
      val currentListOfRemoteToolInfo =
        Seq(RemoteToolInformation(toolSpec("uuid"), None), RemoteToolInformation(toolSpec("uuid 2"), Some(Seq.empty)))
      val newListOfTools = Seq(toolSpec("uuid"))

      val res = storage.mergeToolsList(currentListOfRemoteToolInfo, newListOfTools)
      res shouldEqual Seq(RemoteToolInformation(toolSpec("uuid"), None))
    }

    s"return empty list of remote tool information when empty new list is empty".stripMargin in {
      val currentList =
        Seq(RemoteToolInformation(toolSpec("uuid"), None), RemoteToolInformation(toolSpec("uuid 2"), Some(Seq.empty)))

      val emptyListOfTools = Seq.empty

      val res = storage.mergeToolsList(currentList, emptyListOfTools)
      res shouldEqual Seq.empty
    }

    s"clean patterns list of tool when tool version changes".stripMargin in {
      val currentList = Seq(RemoteToolInformation(toolSpec("uuid 2"), Some(Seq.empty)))

      // Tool with uuid 2 version changed
      val newListOfTools = Seq(toolSpec("uuid"), toolSpec("uuid 2", "1.0.1"))

      val expectedList =
        Seq(RemoteToolInformation(toolSpec("uuid"), None), RemoteToolInformation(toolSpec("uuid 2", "1.0.1"), None))
      val res = storage.mergeToolsList(currentList, newListOfTools)
      res shouldEqual expectedList
    }
  }
}
