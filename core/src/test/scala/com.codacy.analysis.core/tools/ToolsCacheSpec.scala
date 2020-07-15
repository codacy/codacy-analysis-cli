package com.codacy.analysis.core.tools

import com.codacy.analysis.core.clients.{CodacyTool, CodacyToolPattern}
import org.specs2.control.NoLanguageFeatures
import org.specs2.mutable.Specification

class ToolsCacheSpec extends Specification with NoLanguageFeatures {

  val codacyTool = CodacyTool(
    uuid = "1",
    name = "eslint",
    version = "6.8.0",
    shortName = "eslint",
    needsCompilation = false,
    dockerImage = "codacy/codacy-eslint:1.2.0",
    clientSide = false,
    enabledByDefault = false,
    configurable = false)

  val eslintCodacyToolPattern = CodacyToolPattern(
    id = "example_pattern",
    level = "Info",
    category = "CodeStyle",
    title = "Example pattern",
    enabledByDefault = true)

  val toolTuple = (codacyTool, Seq[CodacyToolPattern](eslintCodacyToolPattern))
  val cachedToolsSeq = Seq(toolTuple)

  "mergeToolsList" should {
    "not have duplicated tools" in {
      val otherTool = codacyTool.copy(uuid = "2")
      val newToolsTupleSeq = Seq(toolTuple, (otherTool, Seq[CodacyToolPattern]()))

      val merged = ToolsCache.mergeToolsSeq(newToolsTupleSeq, cachedToolsSeq)

      merged mustEqual newToolsTupleSeq
    }

    "add new tool" in {
      val otherTool = codacyTool.copy(uuid = "2")
      val newToolsTupleSeq = Seq((otherTool, Seq[CodacyToolPattern]()))
      val expectedSeq = newToolsTupleSeq ++ cachedToolsSeq

      val merged = ToolsCache.mergeToolsSeq(newToolsTupleSeq, cachedToolsSeq)

      merged mustEqual expectedSeq
    }

    "replace tool with new one" in {
      val otherTool = codacyTool.copy(version = "7.0.0")
      val newToolsTupleSeq = Seq((otherTool, Seq[CodacyToolPattern]()))

      val merged = ToolsCache.mergeToolsSeq(newToolsTupleSeq, cachedToolsSeq)

      merged mustEqual newToolsTupleSeq
    }

    "return empty" in {
      val merged =  ToolsCache.mergeToolsSeq(Seq.empty, Seq.empty)
      merged mustEqual Seq.empty
    }
  }
}
