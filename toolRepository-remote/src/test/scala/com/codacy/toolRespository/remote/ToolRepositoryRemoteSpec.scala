package com.codacy.toolRespository.remote

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpHeader, HttpResponse}
import akka.stream.ActorMaterializer
import cats.data.EitherT
import com.codacy.analysis.clientapi.definitions._
import com.codacy.analysis.clientapi.tools.{ListToolPatternsResponse, ListToolsResponse, ToolsClient}
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
    configFilenames = Vector.empty,
    version = "version",
    description = None,
    dockerImage = "dockerImage",
    languages = Vector.empty,
    clientSide = false,
    enabled = true,
    configurable = false)

  val toolB = Tool(
    uuid = "uuid - B",
    name = "name B",
    shortName = "shortName",
    documentationUrl = None,
    sourceCodeUrl = None,
    prefix = None,
    needsCompilation = false,
    configFilenames = Vector.empty,
    version = "version",
    description = None,
    dockerImage = "dockerImage",
    languages = Vector.empty,
    clientSide = false,
    enabled = true,
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
        eitherListToolsResponse(ListToolsResponse.OK(ToolListResponse(None, Vector(toolA)))),
        eitherListToolsResponse(ListToolsResponse.OK(ToolListResponse(None, Vector(toolB)))))

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
          languages = ArgumentMatchers.any[Option[Iterable[String]]],
          headers = ArgumentMatchers.any[List[HttpHeader]])).thenReturn(
        eitherListToolsResponse(ListToolsResponse.OK(ToolListResponse(Some(paginationInfo), Vector(toolA)))),
        eitherListToolsResponse(ListToolsResponse.OK(ToolListResponse(None, Vector(toolB)))))

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
    def eitherListToolPatternsResponse(listToolPatternsResponse: ListToolPatternsResponse)
      : EitherT[Future, Either[Throwable, HttpResponse], ListToolPatternsResponse] = {
      val responseEither: Either[Either[Throwable, HttpResponse], ListToolPatternsResponse] =
        Right(listToolPatternsResponse)
      EitherT(Future.successful(responseEither))
    }

    val patternA = Pattern(
      internalId = "internalId - A",
      name = None,
      toolUuid = "toolUuid",
      level = Pattern.Level.Info,
      categoryType = "categoryType",
      subCategory = None,
      description = None,
      explanation = None,
      enabled = true,
      languages = None,
      timeToFix = None,
      parameters = Vector.empty)

    val patternB = Pattern(
      internalId = "internalId - B",
      name = None,
      toolUuid = "toolUuid",
      level = Pattern.Level.Info,
      categoryType = "categoryType",
      subCategory = None,
      description = None,
      explanation = None,
      enabled = true,
      languages = None,
      timeToFix = None,
      parameters = Vector.empty)
    "return the list of patterns" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient)

      when(
        mockedClient.listToolPatterns(
          toolUuid = ArgumentMatchers.any[String],
          languages = ArgumentMatchers.any[Option[Iterable[String]]],
          categories = ArgumentMatchers.any[Option[Iterable[String]]],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]])).thenReturn(
        eitherListToolPatternsResponse(ListToolPatternsResponse.OK(PatternListResponse(None, Vector(patternA)))),
        eitherListToolPatternsResponse(ListToolPatternsResponse.OK(PatternListResponse(None, Vector(patternB)))))

      val patterns = toolRepository.listPatterns("some-tool-uuid")

      // patternB should not be returned because the first request returned an empty cursor
      patterns must haveLength(1)
      patterns.head.id must_== patternA.internalId
    }

    "return list with multiple patterns" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient)

      val paginationInfo = PaginationInfo(Some("cursor"), Some(100), Some(1))

      when(
        mockedClient.listToolPatterns(
          toolUuid = ArgumentMatchers.any[String],
          languages = ArgumentMatchers.any[Option[Iterable[String]]],
          categories = ArgumentMatchers.any[Option[Iterable[String]]],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]])).thenReturn(
        eitherListToolPatternsResponse(
          ListToolPatternsResponse.OK(PatternListResponse(Some(paginationInfo), Vector(patternA)))),
        eitherListToolPatternsResponse(ListToolPatternsResponse.OK(PatternListResponse(None, Vector(patternB)))))

      val patterns = toolRepository.listPatterns("some-tool-uuid")

      // patternB should not be returned because the first request returned an empty cursor
      patterns must haveLength(2)
      patterns.map(_.id) must contain(patternA.internalId)
      patterns.map(_.id) must contain(patternB.internalId)
    }

    "throw an exception if API returns BadRequest" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient)

      when(
        mockedClient.listToolPatterns(
          toolUuid = ArgumentMatchers.any[String],
          languages = ArgumentMatchers.any[Option[Iterable[String]]],
          categories = ArgumentMatchers.any[Option[Iterable[String]]],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]]))
        .thenReturn(eitherListToolPatternsResponse(ListToolPatternsResponse.BadRequest(BadRequest("error"))))

      toolRepository.listPatterns("some-tool-uuid") must throwA[Exception]
    }

    "throw an exception if API returns NotFound" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient)

      when(
        mockedClient.listToolPatterns(
          toolUuid = ArgumentMatchers.any[String],
          languages = ArgumentMatchers.any[Option[Iterable[String]]],
          categories = ArgumentMatchers.any[Option[Iterable[String]]],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]]))
        .thenReturn(eitherListToolPatternsResponse(ListToolPatternsResponse.NotFound(NotFound("error"))))

      toolRepository.listPatterns("some-tool-uuid") must throwA[Exception]
    }

    "throw an exception if API returns InternalServerError" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient)

      when(
        mockedClient.listToolPatterns(
          toolUuid = ArgumentMatchers.any[String],
          languages = ArgumentMatchers.any[Option[Iterable[String]]],
          categories = ArgumentMatchers.any[Option[Iterable[String]]],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]])).thenReturn(
        eitherListToolPatternsResponse(ListToolPatternsResponse.InternalServerError(InternalServerError("error"))))

      toolRepository.listPatterns("some-tool-uuid") must throwA[Exception]
    }
  }
}
