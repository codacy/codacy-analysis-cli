package com.codacy.toolRespository.remote

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpHeader, HttpResponse}
import akka.stream.ActorMaterializer
import cats.data.EitherT
import com.codacy.analysis.clientapi.definitions._
import com.codacy.analysis.clientapi.tools.{
  ListDuplicationToolsResponse,
  ListPatternsResponse,
  ListToolsResponse,
  ToolsClient
}
import com.codacy.analysis.core.model.{AnalyserError, PatternSpec, ToolSpec}
import com.codacy.analysis.core.storage.DataStorageStub
import com.codacy.plugins.api.languages.Languages
import com.codacy.toolRepository.remote.ToolRepositoryRemote
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.specs2.matcher.EitherMatchers
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ToolRepositoryRemoteSpec extends Specification with Mockito with EitherMatchers {
  implicit val actorSystem = ActorSystem("MyTest")
  implicit val materializer = ActorMaterializer()

  private def getTool(uuid: String, name: String, standalone: Boolean = false) =
    Tool(
      uuid = uuid,
      name = name,
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
      standalone = standalone,
      enabledByDefault = true,
      configurable = false)

  private def getPattern(id: String) =
    Pattern(
      id = id,
      title = None,
      level = PatternDetails.Level.Info,
      severityLevel = SeverityLevel.Info,
      category = "categoryType",
      subCategory = None,
      description = None,
      explanation = None,
      enabled = true,
      timeToFix = None,
      parameters = Vector.empty)

  private def toolSpec(uuid: String, version: String = "version"): ToolSpec =
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
      standalone = false,
      hasUIConfiguration = false)

  private def patternSpec(id: String): PatternSpec =
    PatternSpec(
      id = id,
      title = "",
      level = "Info",
      category = "categoryType",
      subCategory = None,
      description = None,
      explanation = None,
      enabled = true,
      timeToFix = None,
      parameters = Seq.empty,
      languages = Set.empty)

  private val toolA: Tool = getTool("uuid - A", "name A")
  private val toolB: Tool = getTool("uuid - B", "name B")

  private val duplicationToolA: DuplicationTool = DuplicationTool("toolA:latest", Vector("Scala"))
  private val duplicationToolB: DuplicationTool = DuplicationTool("toolB:latest", Vector("Java", "Javascript"))

  private val patternA: Pattern = getPattern("internalId - A")
  private val patternB: Pattern = getPattern("internalId - B")

  def emptyStorage[T] =
    new DataStorageStub[T] {
      override def save(values: Seq[T]): Boolean = true
      override def get(): Option[Seq[T]] = None
    }

  def mockStorage[T](content: Seq[T]) =
    new DataStorageStub[T] {
      override def save(values: Seq[T]): Boolean = true
      override def get(): Option[Seq[T]] = Some(content)
    }

  val mockToolsDataWithStorage = mockStorage(Seq(toolSpec(toolA.uuid)))

  val mockPatternDataWithStorage = (_: String) => mockStorage(Seq(patternSpec(patternA.id)))

  def toEitherT[T](value: T): EitherT[Future, Either[Throwable, HttpResponse], T] = {
    val valueEither: Either[Either[Throwable, HttpResponse], T] = Right(value)
    EitherT(Future.successful(valueEither))
  }

  def eitherListToolPatternsResponse(listToolPatternsResponse: ListPatternsResponse)
    : EitherT[Future, Either[Throwable, HttpResponse], ListPatternsResponse] = {
    val responseEither: Either[Either[Throwable, HttpResponse], ListPatternsResponse] =
      Right(listToolPatternsResponse)
    EitherT(Future.successful(responseEither))
  }

  "listSupportedTools" should {

    "return the list of supported tools" in {
      // Arrange
      val mockedClient = mock[ToolsClient]
      val toolRepository =
        new ToolRepositoryRemote(mockedClient, emptyStorage, null, null, _ => emptyStorage)

      when(mockedClient.listTools(cursor = None)).thenReturn(
        toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(toolA), None))),
        toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(toolB), None))))

      val toolsEither = toolRepository.listSupportedTools()

      toolsEither must beRight
      // toolB should not be returned because the first one returned an empty cursor
      toolsEither must beRight((t: Seq[ToolSpec]) => t must haveLength(1))
      toolsEither must beRight((x: Seq[ToolSpec]) => x.head.uuid must_== toolA.uuid)
    }

    "return list with multiple tools" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository =
        new ToolRepositoryRemote(mockedClient, emptyStorage, null, null, _ => emptyStorage)

      val paginationInfo = PaginationInfo(Some("cursor"), Some(100), Some(1))

      when(
        mockedClient.listTools(
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]])).thenReturn(
        toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(toolA), Some(paginationInfo)))),
        toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(toolB), None))))

      val toolsEither = toolRepository.listSupportedTools()

      toolsEither must beRight
      // toolB should not be returned because the first one returned an empty cursor
      toolsEither must beRight((t: Seq[ToolSpec]) => t must haveLength(2))
      toolsEither must beRight((t: Seq[ToolSpec]) => t.map(_.uuid) must contain(toolA.uuid))
      toolsEither must beRight((t: Seq[ToolSpec]) => t.map(_.uuid) must contain(toolB.uuid))
    }

    "return list with stored tools" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository =
        new ToolRepositoryRemote(mockedClient, mockToolsDataWithStorage, null, null, _ => emptyStorage)

      mockedClient.listTools(cursor = None).returns(toEitherT(ListToolsResponse.BadRequest(BadRequest("error"))))

      val toolsEither = toolRepository.listSupportedTools()

      toolsEither must beRight
      toolsEither must beRight((t: Seq[ToolSpec]) => t must haveLength(1))
      toolsEither must beRight((x: Seq[ToolSpec]) => x.head.uuid must_== toolA.uuid)
    }

    "not return standalone tools" in {
      // Arrange
      val mockedClient = mock[ToolsClient]
      val toolRepository =
        new ToolRepositoryRemote(mockedClient, emptyStorage, null, null, _ => emptyStorage)

      val standalone = getTool("", "", standalone = true)

      mockedClient
        .listTools(cursor = None)
        .returns(toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(standalone, toolA), None))))

      // Act
      val toolsEither = toolRepository.listSupportedTools()

      // Assert
      toolsEither must beRight
      toolsEither must beRight((t: Seq[ToolSpec]) => t must haveLength(1))
      toolsEither must beRight((x: Seq[ToolSpec]) => x.head.uuid must_== toolA.uuid)
    }

    "throw an exception if API returns BadRequest" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository =
        new ToolRepositoryRemote(mockedClient, emptyStorage, null, null, _ => emptyStorage)

      mockedClient.listTools(cursor = None).returns(toEitherT(ListToolsResponse.BadRequest(BadRequest("error"))))

      toolRepository.listSupportedTools() must beLeft(
        (e: AnalyserError) => e must beAnInstanceOf[AnalyserError.FailedToFetchTools])
    }

    "throw an exception if API returns InternalServerError" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository =
        new ToolRepositoryRemote(mockedClient, emptyStorage, null, null, _ => emptyStorage)

      mockedClient
        .listTools(cursor = None)
        .returns(toEitherT(ListToolsResponse.InternalServerError(InternalServerError("error"))))

      toolRepository.listSupportedTools() must beLeft(
        (e: AnalyserError) => e must beAnInstanceOf[AnalyserError.FailedToFetchTools])
    }
  }

  "getTool" should {

    "fail with standalone tools" in {
      // Arrange
      val mockedClient = mock[ToolsClient]
      val toolRepository =
        new ToolRepositoryRemote(mockedClient, emptyStorage, null, null, _ => emptyStorage)

      val standaloneUuid = "standalone"
      val standalone = getTool(standaloneUuid, "", standalone = true)

      mockedClient
        .listTools(cursor = None)
        .returns(toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(standalone), None))))

      // Act
      val toolEither = toolRepository.getTool(standaloneUuid)

      // Assert
      toolEither must beLeft((e: AnalyserError) => e must beAnInstanceOf[AnalyserError.StandaloneToolInput])
    }
  }

  "listDuplicationTools" should {

    "return the list of tools" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient, null, emptyStorage, null, null)

      mockedClient
        .listDuplicationTools()
        .returns(toEitherT(
          ListDuplicationToolsResponse.OK(DuplicationToolListResponse(Vector(duplicationToolA, duplicationToolB)))))

      val duplicationToolsEither = toolRepository.listDuplicationTools

      duplicationToolsEither must beRight
      // toolB should not be returned because the first one returned an empty cursor
      val duplicationTools = duplicationToolsEither.right.get
      duplicationTools must haveLength(2)
      duplicationTools(0).dockerImage must_== duplicationToolA.dockerImage
      duplicationTools(1).dockerImage must_== duplicationToolB.dockerImage
    }

    "return list with multiple tools" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository =
        new ToolRepositoryRemote(mockedClient, emptyStorage, null, null, _ => emptyStorage)

      val paginationInfo = PaginationInfo(Some("cursor"), Some(100), Some(1))

      when(
        mockedClient.listTools(
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]])).thenReturn(
        toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(toolA), Some(paginationInfo)))),
        toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(toolB), None))))

      val toolsEither = toolRepository.listSupportedTools()

      toolsEither must beRight
      // toolB should not be returned because the first one returned an empty cursor
      toolsEither must beRight((t: Seq[ToolSpec]) => t must haveLength(2))
      toolsEither must beRight((t: Seq[ToolSpec]) => t.map(_.uuid) must contain(toolA.uuid))
      toolsEither must beRight((t: Seq[ToolSpec]) => t.map(_.uuid) must contain(toolB.uuid))
    }
  }

  "listPatterns" should {

    "return the list of patterns from API" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository =
        new ToolRepositoryRemote(mockedClient, emptyStorage, null, null, _ => emptyStorage)

      when(mockedClient.listTools(cursor = None)).thenReturn(
        toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(toolA), None))),
        toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(toolB), None))))

      when(
        mockedClient.listPatterns(
          toolUuid = ArgumentMatchers.any[String],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]])).thenReturn(
        eitherListToolPatternsResponse(ListPatternsResponse.OK(PatternListResponse(Vector(patternA), None))),
        eitherListToolPatternsResponse(ListPatternsResponse.OK(PatternListResponse(Vector(patternB), None))))

      val patternsEither = toolRepository.listPatterns(toolSpec("some-tool-uuid"))

      patternsEither must beRight
      // patternB should not be returned because the first request returned an empty cursor
      patternsEither must beRight((p: Seq[PatternSpec]) => p must haveLength(1))
      patternsEither must beRight((p: Seq[PatternSpec]) => p.head.id must_== patternA.id)
    }

    "return list with multiple patterns from API" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository =
        new ToolRepositoryRemote(mockedClient, emptyStorage, null, null, _ => emptyStorage)

      val paginationInfo = PaginationInfo(Some("cursor"), Some(100), Some(1))

      mockedClient
        .listTools(cursor = None)
        .returns(toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(toolA), None))))

      when(
        mockedClient.listPatterns(
          toolUuid = ArgumentMatchers.any[String],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]])).thenReturn(
        eitherListToolPatternsResponse(
          ListPatternsResponse.OK(PatternListResponse(Vector(patternA), Some(paginationInfo)))),
        eitherListToolPatternsResponse(ListPatternsResponse.OK(PatternListResponse(Vector(patternB), None))))

      val patternsEither = toolRepository.listPatterns(toolSpec("some-tool-uuid"))

      patternsEither must beRight
      // patternB should not be returned because the first request returned an empty cursor
      patternsEither must beRight((p: Seq[PatternSpec]) => p must haveLength(2))
      patternsEither must beRight((p: Seq[PatternSpec]) => p.map(_.id) must contain(patternA.id))
      patternsEither must beRight((p: Seq[PatternSpec]) => p.map(_.id) must contain(patternB.id))
    }

    "return list of patterns from storage" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository =
        new ToolRepositoryRemote(mockedClient, mockToolsDataWithStorage, null, null, mockPatternDataWithStorage)

      mockedClient
        .listTools(cursor = None)
        .returns(toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(toolA), None))))

      when(
        mockedClient.listPatterns(
          toolUuid = ArgumentMatchers.any[String],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]]))
        .thenReturn(eitherListToolPatternsResponse(ListPatternsResponse.BadRequest(BadRequest("error"))))

      val patternsEither = toolRepository.listPatterns(toolSpec("some-tool-uuid"))

      patternsEither must beRight
      patternsEither must beRight((p: Seq[PatternSpec]) => p must haveLength(1))
      patternsEither must beRight((p: Seq[PatternSpec]) => p.map(_.id) must contain(patternA.id))
    }

    "return list of patterns first from storage" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository =
        new ToolRepositoryRemote(mockedClient, mockToolsDataWithStorage, null, null, mockPatternDataWithStorage)

      mockedClient
        .listTools(cursor = None)
        .returns(toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(toolA, toolB), None))))

      when(
        mockedClient.listPatterns(
          toolUuid = ArgumentMatchers.any[String],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]]))
        .thenReturn(eitherListToolPatternsResponse(ListPatternsResponse.BadRequest(BadRequest("error"))))

      val patternsEither = toolRepository.listPatterns(toolSpec(toolB.uuid))

      patternsEither must beRight
      patternsEither must beRight((p: Seq[PatternSpec]) => p must haveLength(1))
      patternsEither must beRight((p: Seq[PatternSpec]) => p.map(_.id) must contain(patternA.id))
    }

    "throw an exception if API returns BadRequest" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository =
        new ToolRepositoryRemote(mockedClient, emptyStorage, null, null, _ => emptyStorage)

      mockedClient
        .listTools(cursor = None)
        .returns(toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(toolA), None))))

      when(
        mockedClient.listPatterns(
          toolUuid = ArgumentMatchers.any[String],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]]))
        .thenReturn(eitherListToolPatternsResponse(ListPatternsResponse.BadRequest(BadRequest("error"))))

      toolRepository.listPatterns(toolSpec("some-tool-uuid")) must beLeft(
        (e: AnalyserError) => e must beAnInstanceOf[AnalyserError.FailedToListPatterns])
    }

    "throw an exception if API returns NotFound" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository = new ToolRepositoryRemote(mockedClient, emptyStorage, null, null, _ => emptyStorage)

      mockedClient
        .listTools(cursor = None)
        .returns(toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(toolA), None))))

      when(
        mockedClient.listPatterns(
          toolUuid = ArgumentMatchers.any[String],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]]))
        .thenReturn(eitherListToolPatternsResponse(ListPatternsResponse.NotFound(NotFound("error"))))

      toolRepository.listPatterns(toolSpec("some-tool-uuid")) must beLeft(
        (e: AnalyserError) => e must beAnInstanceOf[AnalyserError.FailedToListPatterns])
    }

    "throw an exception if API returns InternalServerError" in {
      val mockedClient = mock[ToolsClient]
      val toolRepository =
        new ToolRepositoryRemote(mockedClient, emptyStorage, null, null, _ => emptyStorage)

      mockedClient
        .listTools(cursor = None)
        .returns(toEitherT(ListToolsResponse.OK(ToolListResponse(Vector(toolA), None))))

      when(
        mockedClient.listPatterns(
          toolUuid = ArgumentMatchers.any[String],
          cursor = ArgumentMatchers.any[Option[String]],
          limit = ArgumentMatchers.any[Option[Int]],
          headers = ArgumentMatchers.any[List[HttpHeader]])).thenReturn(
        eitherListToolPatternsResponse(ListPatternsResponse.InternalServerError(InternalServerError("error"))))

      toolRepository.listPatterns(toolSpec("some-tool-uuid")) must beLeft(
        (e: AnalyserError) => e must beAnInstanceOf[AnalyserError.FailedToListPatterns])
    }
  }
}
