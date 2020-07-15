package com.codacy.analysis.core.tools

import better.files.File
import com.codacy.analysis.core.clients.CodacyToolJsonEncoders._
import com.codacy.analysis.core.clients.{CodacyTool, CodacyToolPattern}
import io.circe._
import io.circe.syntax._
import org.log4s.{Logger, getLogger}

object ToolsCache {

  private val logger: Logger = getLogger

  private val toolsCacheFile: File = File.currentWorkingDirectory / ".cache" / "tools-cache"

  private def writeToFile(file: File, content: String): Boolean = {
    file.createFileIfNotExists(createParents = true)
    file.write(content)
    file.contentAsString == content
  }

  def invalidate(): Boolean = {
    logger.debug("Invalidating tools cache")
    toolsCacheFile.delete().exists
  }

  def save(codacyToolsInformation: Seq[(CodacyTool, Seq[CodacyToolPattern])]): Unit = {
    logger.debug("Updating tools cache")
    val newCachedList = retrieve match {
      case None              => codacyToolsInformation
      case Some(cachedTools) => mergeToolsSeq(codacyToolsInformation, cachedTools)
    }

    val cachedListJson = newCachedList.asJson.toString
    val wroteSuccessfully = writeToFile(toolsCacheFile, cachedListJson)
    logger.debug(s"Cached saved with status: ${wroteSuccessfully}")
  }

  def retrieve: Option[Seq[(CodacyTool, Seq[CodacyToolPattern])]] = {
    logger.debug("Retrieving cached tools")
    if (toolsCacheFile.exists) {
      parser
        .decode[Seq[(CodacyTool, Seq[CodacyToolPattern])]](toolsCacheFile.contentAsString)
        .fold(_ => None, v => Some(v))
    } else {
      None
    }
  }

  private def addTool(acc: Seq[(CodacyTool, scala.Seq[CodacyToolPattern])],
                      value: (CodacyTool, scala.Seq[CodacyToolPattern])) = {
    // if already exists in the new list, don't add it again
    if (acc.exists(_._1.uuid == value._1.uuid)) {
      acc
    } else {
      acc :+ value
    }
  }

  private[tools] def mergeToolsSeq(newTools: Seq[(CodacyTool, Seq[CodacyToolPattern])],
                                   cachedTools: Seq[(CodacyTool, Seq[CodacyToolPattern])]) = {
    logger.debug("Merging cached tools with new tools list")
    val fullList = newTools ++ cachedTools // newTools should be first so it updates cache for already existing tools
    fullList.foldLeft(Seq[(CodacyTool, Seq[CodacyToolPattern])]()) {
      case (acc, value) => addTool(acc, value)
    }
  }
}
