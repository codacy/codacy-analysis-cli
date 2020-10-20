package com.codacy.analysis.cli.toolRepository

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.codacy.analysis.clientapi.tools.ToolsClient
import com.codacy.analysis.core.tools.ToolRepository
import com.codacy.toolRepository.plugins.ToolRepositoryPlugins
import com.codacy.toolRepository.remote.{RemoteToolsDataStorage, ToolRepositoryRemote}

import scala.concurrent.Future

object ToolRepositoryFactory {

  def build(codacyApiBaseUrl: String, fetchRemoteTools: Boolean): ToolRepository =
    if (fetchRemoteTools) {
      val actorSystem = ActorSystem("ToolsServiceActorSystem")
      val materializer = akka.stream.ActorMaterializer()(actorSystem)
      val httpClient: HttpRequest => Future[HttpResponse] =
        Http(actorSystem).singleRequest(_)

      val toolsClient =
        ToolsClient(codacyApiBaseUrl)(httpClient = httpClient, ec = actorSystem.dispatcher, mat = materializer)
      val remoteToolsDataStorage = new RemoteToolsDataStorage()
      new ToolRepositoryRemote(toolsClient, remoteToolsDataStorage)(ec = actorSystem.dispatcher, mat = materializer)
    } else {
      new ToolRepositoryPlugins()
    }
}
