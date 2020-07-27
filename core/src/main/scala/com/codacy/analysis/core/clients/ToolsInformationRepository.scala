package com.codacy.analysis.core.clients

import scala.concurrent.Future

trait ToolsInformationRepository {
  def toolsList: Future[Either[String, Set[CodacyTool]]]

  def toolPatterns(toolUuid: String): Future[Seq[CodacyToolPattern]]
}
