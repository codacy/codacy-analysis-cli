package com.codacy.analysis.cli.toolRepository

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.codacy.analysis.clientapi.tools.ToolsClient
import com.codacy.analysis.core.tools.ToolRepository
import com.codacy.toolRepository.remote.ToolRepositoryRemote
import com.codacy.toolRepository.remote.storage.{PatternSpecDataStorage, ToolSpecDataStorage}

import scala.concurrent.Future

object ToolRepositoryFactory {

  def build(codacyApiBaseUrl: String): ToolRepository = {
    val actorSystem = ActorSystem("ToolsServiceActorSystem")
    val materializer = akka.stream.ActorMaterializer()(actorSystem)
    val httpClient: HttpRequest => Future[HttpResponse] =
      Http(actorSystem).singleRequest(_)

    val toolsClient =
      ToolsClient(codacyApiBaseUrl)(httpClient = httpClient, ec = actorSystem.dispatcher, mat = materializer)
    new ToolRepositoryRemote(toolsClient, ToolSpecDataStorage.apply, PatternSpecDataStorage.apply)(
      ec = actorSystem.dispatcher,
      mat = materializer)
  }
}
