package com.codacy.toolRespository.remote

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpHeader, HttpResponse}
import akka.stream.ActorMaterializer
import cats.data.EitherT
import com.codacy.analysis.clientapi.definitions._
import com.codacy.analysis.clientapi.tools.{ListPatternsResponse, ListToolsResponse, ToolsClient}
import com.codacy.toolRepository.remote.ToolRepositoryRemote
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ToolRepositoryRemoteSpec extends Specification with Mockito {
  implicit val actorSystem = ActorSystem("MyTest")
  implicit val materializer = ActorMaterializer()

  val toolA = Tool(
    uuid = "uuid - A",
    name = "name A",
    shortName = "shortName",
    documentationUrl = None,
    sourceCodeUrl = None,
    prefix = None,
    needsCompilation = false,
    configurationFilenames = Vector.empty,
    version = "version",
    description = None,
    dockerImage = "dockerImage",
    languages = Vector.empty,
    clientSide = false,
    enabledByDefault = true,
    configurable = false)

  val toolB = Tool(
    uuid = "uuid - B",
    name = "name B",
    shortName = "shortName",
    documentationUrl = None,
    sourceCodeUrl = None,
    prefix = None,
    needsCompilation = false,
    configurationFilenames = Vector.empty,
    version = "version",
    description = None,
    dockerImage = "dockerImage",
    languages = Vector.empty,
    clientSide = false,
    enabledByDefault = true,
    configurable = false)

  "list" should {
    def eitherListToolsResponse(
      listToolsResponse: ListToolsResponse): EitherT[Future, Either[Throwable, HttpResponse], ListToolsResponse] = {
      val responseEither: Either[Either[Throwable, HttpResponse], ListToolsResponse] = Right(listToolsResponse)
      EitherT(Future.successful(responseEither))
    }

    "return the list of tools" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient)

      when(mockedClient.listTools(cursor = None)).thenReturn(
        eitherListToolsResponse(ListToolsResponse.OK(ToolListResponse(Vector(toolA), None))),
        eitherListToolsResponse(ListToolsResponse.OK(ToolListResponse(Vector(toolB), None))))

      val tools = toolRepository.list()

      // toolB should not be returned because the first one returned an empty cursor
      tools must haveLength(1)
      tools.head.uuid must_== (toolA.uuid)
    }

    "return list with multiple tools" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient)

      val paginationInfo = PaginationInfo(Some("cursor"), Some(100), Some(1))

      when(
        mockedClient.listTools(
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]])).thenReturn(
        eitherListToolsResponse(ListToolsResponse.OK(ToolListResponse(Vector(toolA), Some(paginationInfo)))),
        eitherListToolsResponse(ListToolsResponse.OK(ToolListResponse(Vector(toolB), None))))

      val tools = toolRepository.list()

      // toolB should not be returned because the first one returned an empty cursor
      tools must haveLength(2)
      tools.map(_.uuid) must contain(toolA.uuid)
      tools.map(_.uuid) must contain(toolB.uuid)
    }

    "throw an exception if API returns BadRequest" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient)

      when(mockedClient.listTools(cursor = None))
        .thenReturn(eitherListToolsResponse(ListToolsResponse.BadRequest(BadRequest("error"))))

      toolRepository.list() must throwA[Exception]
    }

    "throw an exception if API returns InternalServerError" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient)

      when(mockedClient.listTools(cursor = None))
        .thenReturn(eitherListToolsResponse(ListToolsResponse.InternalServerError(InternalServerError("error"))))

      toolRepository.list() must throwA[Exception]
    }
  }

  "listPatterns" should {
    def eitherListToolPatternsResponse(listToolPatternsResponse: ListPatternsResponse)
      : EitherT[Future, Either[Throwable, HttpResponse], ListPatternsResponse] = {
      val responseEither: Either[Either[Throwable, HttpResponse], ListPatternsResponse] =
        Right(listToolPatternsResponse)
      EitherT(Future.successful(responseEither))
    }

    val patternA = Pattern(
      id = "internalId - A",
      title = None,
      level = "Info",
      category = "categoryType",
      subCategory = None,
      description = None,
      explanation = None,
      enabled = true,
      timeToFix = None,
      parameters = Vector.empty)

    val patternB = Pattern(
      id = "internalId - B",
      title = None,
      level = "Info",
      category = "categoryType",
      subCategory = None,
      description = None,
      explanation = None,
      enabled = true,
      timeToFix = None,
      parameters = Vector.empty)
    "return the list of patterns" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient)

      when(
        mockedClient.listPatterns(
          toolId = ArgumentMatchers.any[String],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]])).thenReturn(
        eitherListToolPatternsResponse(ListPatternsResponse.OK(PatternListResponse(Vector(patternA), None))),
        eitherListToolPatternsResponse(ListPatternsResponse.OK(PatternListResponse(Vector(patternB), None))))

      val patterns = toolRepository.listPatterns("some-tool-uuid")

      // patternB should not be returned because the first request returned an empty cursor
      patterns must haveLength(1)
      patterns.head.id must_== patternA.id
    }

    "return list with multiple patterns" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient)

      val paginationInfo = PaginationInfo(Some("cursor"), Some(100), Some(1))

      when(
        mockedClient.listPatterns(
          toolId = ArgumentMatchers.any[String],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]])).thenReturn(
        eitherListToolPatternsResponse(
          ListPatternsResponse.OK(PatternListResponse(Vector(patternA), Some(paginationInfo)))),
        eitherListToolPatternsResponse(ListPatternsResponse.OK(PatternListResponse(Vector(patternB), None))))

      val patterns = toolRepository.listPatterns("some-tool-uuid")

      // patternB should not be returned because the first request returned an empty cursor
      patterns must haveLength(2)
      patterns.map(_.id) must contain(patternA.id)
      patterns.map(_.id) must contain(patternB.id)
    }

    "throw an exception if API returns BadRequest" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient)

      when(
        mockedClient.listPatterns(
          toolId = ArgumentMatchers.any[String],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]]))
        .thenReturn(eitherListToolPatternsResponse(ListPatternsResponse.BadRequest(BadRequest("error"))))

      toolRepository.listPatterns("some-tool-uuid") must throwA[Exception]
    }

    "throw an exception if API returns NotFound" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient)

      when(
        mockedClient.listPatterns(
          toolId = ArgumentMatchers.any[String],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]]))
        .thenReturn(eitherListToolPatternsResponse(ListPatternsResponse.NotFound(NotFound("error"))))

      toolRepository.listPatterns("some-tool-uuid") must throwA[Exception]
    }

    "throw an exception if API returns InternalServerError" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient)

      when(
        mockedClient.listPatterns(
          toolId = ArgumentMatchers.any[String],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]])).thenReturn(
        eitherListToolPatternsResponse(ListPatternsResponse.InternalServerError(InternalServerError("error"))))

      toolRepository.listPatterns("some-tool-uuid") must throwA[Exception]
    }
  }
}
