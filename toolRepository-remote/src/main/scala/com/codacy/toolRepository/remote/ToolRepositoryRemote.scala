package com.codacy.toolRepository.remote

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.codacy.analysis.clientapi.definitions
import com.codacy.analysis.clientapi.definitions.{
  DuplicationToolListResponse,
  MetricsToolListResponse,
  PaginationInfo,
  PatternListResponse,
  ToolListResponse
}
import com.codacy.analysis.clientapi.tools.{
  ListDuplicationToolsResponse,
  ListMetricsToolsResponse,
  ListPatternsResponse,
  ListToolsResponse,
  ToolsClient
}
import com.codacy.analysis.core.model.{
  AnalyserError,
  DuplicationToolSpec,
  MetricsToolSpec,
  ParameterSpec,
  PatternSpec,
  ToolSpec
}
import com.codacy.analysis.core.storage.DataStorage
import com.codacy.analysis.core.tools.ToolRepository
import com.codacy.plugins.api.languages.{Language, Languages}
import org.log4s.{Logger, getLogger}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class ToolRepositoryRemote(toolsClient: ToolsClient,
                           toolsDataStorage: DataStorage[ToolSpec],
                           duplicationToolsDataStorage: DataStorage[DuplicationToolSpec],
                           metricsToolsDataStorage: DataStorage[MetricsToolSpec],
                           patternsDataStorageFunc: String => DataStorage[PatternSpec])(
  implicit val ec: ExecutionContext,
  implicit val mat: Materializer)
    extends ToolRepository {
  private val logger: Logger = getLogger

  override lazy val allTools: Either[AnalyserError, Seq[ToolSpec]] = {
    val source = PaginatedApiSourceFactory { cursor =>
      toolsClient.listTools(cursor).value.map {
        case Right(ListToolsResponse.OK(ToolListResponse(data, None | Some(PaginationInfo(None, _, _))))) =>
          (None, data)
        case Right(ListToolsResponse.OK(ToolListResponse(data, Some(PaginationInfo(newCursor: Some[String], _, _))))) =>
          (newCursor, data)
        case Right(ListToolsResponse.BadRequest(badRequest)) =>
          throw new Exception(s"Bad Request: ${badRequest.message}")
        case Right(ListToolsResponse.InternalServerError(internalServerError)) =>
          throw new Exception(s"Internal Server Error: ${internalServerError.message}")
        case Left(Right(error)) => throw new Exception(s"Failed to list tools with ${error.status} status code")
        case Left(Left(throwable)) =>
          throw new Exception(throwable.getMessage, throwable)
      }
    }

    val toolsF: Future[Either[AnalyserError, Seq[ToolSpec]]] =
      source.runWith(Sink.seq).map(tools => Right(tools.map(toToolSpec))).recover {
        case e: Exception => Left(AnalyserError.FailedToFetchTools(e.getMessage))
      }
    val result = Await.result(toolsF, 1 minute)
    result match {
      case Right(tools) =>
        toolsDataStorage.save(tools)
        Right(tools)
      case Left(err) =>
        toolsDataStorage.get().toRight(err)
    }
  }

  override lazy val listDuplicationTools: Either[AnalyserError, Seq[DuplicationToolSpec]] = {
    val toolsF = toolsClient.listDuplicationTools().value.map {
      case Right(ListDuplicationToolsResponse.OK(DuplicationToolListResponse(data))) =>
        Right(data.map(toDuplicationToolSpec))
      case Right(ListDuplicationToolsResponse.InternalServerError(internalServerError)) =>
        Left(s"Internal Server Error: ${internalServerError.message}")
      case Left(Right(error))    => Left(s"Failed to list tools with ${error.status} status code")
      case Left(Left(throwable)) => Left(throwable.getMessage)
    }
    Await.result(toolsF, 1.minute) match {
      case Right(tools) =>
        duplicationToolsDataStorage.save(tools)
        Right(tools)
      case Left(err) =>
        duplicationToolsDataStorage.get().toRight(AnalyserError.FailedToFetchTools(err))
    }
  }

  override lazy val listMetricsTools: Either[AnalyserError, Seq[MetricsToolSpec]] = {
    val toolsF = toolsClient.listMetricsTools().value.map {
      case Right(ListMetricsToolsResponse.OK(MetricsToolListResponse(data))) =>
        Right(data.map(toMetricsToolSpec))
      case Right(ListMetricsToolsResponse.InternalServerError(internalServerError)) =>
        Left(s"Internal Server Error: ${internalServerError.message}")
      case Left(Right(error))    => Left(s"Failed to list tools with ${error.status} status code")
      case Left(Left(throwable)) => Left(throwable.getMessage)
    }
    Await.result(toolsF, 1.minute) match {
      case Right(tools) =>
        metricsToolsDataStorage.save(tools)
        Right(tools)
      case Left(err) =>
        metricsToolsDataStorage.get().toRight(AnalyserError.FailedToFetchTools(err))
    }
  }

  private def patternsFromApi(tool: ToolSpec): Either[AnalyserError, Seq[PatternSpec]] = {
    val source = PaginatedApiSourceFactory { cursor =>
      toolsClient.listPatterns(tool.uuid, cursor = cursor).value.map {
        case Right(ListPatternsResponse.OK(PatternListResponse(data, None | Some(PaginationInfo(None, _, _))))) =>
          (None, data)
        case Right(
              ListPatternsResponse.OK(
                PatternListResponse(data, Some(PaginationInfo(newCursor: Some[String], _, _))))) =>
          (newCursor, data)
        case Right(ListPatternsResponse.NotFound(notFoundError)) =>
          throw new Exception(s"Tool does not exist: ${notFoundError.message}")
        case Right(ListPatternsResponse.BadRequest(badRequestError)) =>
          throw new Exception(s"Bad Request: ${badRequestError.message}")
        case Right(ListPatternsResponse.InternalServerError(internalServerError)) =>
          throw new Exception(s"Internal Server Error: ${internalServerError.message}")
        case Left(Right(e)) =>
          throw new Exception(s"API returned status code: ${e.status.value}")
        case Left(Left(t)) =>
          throw new Exception(t.getMessage, t)
      }
    }

    val patternsF =
      source.runWith(Sink.seq).map(patterns => Right(patterns.map(toPatternSpec(_, tool.prefix)))).recover {
        case e: Exception => Left(AnalyserError.FailedToListPatterns(tool.uuid, e.getMessage))
      }

    Await.result(patternsF, 1 minute)
  }

  private def downloadPatternsFromApi(tool: ToolSpec,
                                      storage: DataStorage[PatternSpec]): Either[AnalyserError, Seq[PatternSpec]] = {
    patternsFromApi(tool) match {
      case Right(patternsFromApi) =>
        logger.info(s"Fetched patterns for ${tool.name} version ${tool.version}")
        storage.save(patternsFromApi)
        Right(patternsFromApi)
      case Left(err) =>
        logger.error(s"Failed to fetch patterns for ${tool.name} from API: ${err.message}")
        Left(err)
    }
  }

  override def listPatterns(tool: ToolSpec): Either[AnalyserError, Seq[PatternSpec]] = {
    val toolPatternsStorageFilename = s"${tool.uuid}-${tool.version}"
    val toolPatternsStorageInstance = patternsDataStorageFunc(toolPatternsStorageFilename)

    toolPatternsStorageInstance.get() match {
      case Some(patternsFromStorage) =>
        logger.info(s"Using patterns from cache for ${tool.name} version ${tool.version}")
        Right(patternsFromStorage)
      case None =>
        downloadPatternsFromApi(tool, toolPatternsStorageInstance)
    }
  }

  private def toDuplicationToolSpec(tool: definitions.DuplicationTool): DuplicationToolSpec = {
    val languages = tool.languages.flatMap(Languages.fromName).to[Set]
    DuplicationToolSpec(tool.dockerImage, languages)
  }

  private def toMetricsToolSpec(tool: definitions.MetricsTool): MetricsToolSpec = {
    val languages = tool.languages.flatMap(Languages.fromName).to[Set]
    MetricsToolSpec(tool.dockerImage, languages)
  }

  private def toToolSpec(tool: definitions.Tool): ToolSpec = {
    val languages = tool.languages.flatMap(Languages.fromName).to[Set]
    ToolSpec(
      uuid = tool.uuid,
      dockerImage = tool.dockerImage,
      isDefault = tool.enabledByDefault,
      version = tool.version,
      languages = languages,
      name = tool.name,
      shortName = tool.shortName,
      documentationUrl = tool.documentationUrl,
      sourceCodeUrl = tool.sourceCodeUrl,
      prefix = tool.prefix.getOrElse(""),
      needsCompilation = tool.needsCompilation,
      hasConfigFile = tool.configurationFilenames.nonEmpty,
      configFilenames = tool.configurationFilenames.toSet,
      standalone = tool.standalone,
      hasUIConfiguration = tool.configurable)
  }

  private def toPatternSpec(pattern: definitions.Pattern, patternPrefix: String): PatternSpec = {
    val parameterSpecs = pattern.parameters.map { parameter =>
      ParameterSpec(parameter.name, parameter.default, parameter.description)
    }

    val languages: Set[Language] = pattern.languages
      .getOrElse(Set.empty)
      .flatMap { languageName =>
        Languages.fromName(languageName)
      }(collection.breakOut)

    PatternSpec(
      // plugins is expecting to receive patterns without prefixes
      pattern.id.stripPrefix(patternPrefix),
      pattern.level.value,
      pattern.category,
      pattern.subCategory,
      pattern.title.getOrElse(pattern.id),
      pattern.description,
      pattern.explanation,
      pattern.enabled,
      pattern.timeToFix,
      parameterSpecs,
      languages = languages)
  }
}
