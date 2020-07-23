package com.codacy.analysis.core.clients

import scala.collection.immutable
import scala.concurrent.Future

trait ToolsInformationRepository {
  def toolsList: Future[Either[String, Set[CodacyTool]]]

  def toolPatterns(toolUuid: String): Future[immutable.Seq[CodacyToolPattern]]
}
