package com.codacy.toolRepository.remote

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.codacy.analysis.clientapi.definitions
import com.codacy.analysis.clientapi.definitions.{PaginationInfo, PatternListResponse, ToolListResponse}
import com.codacy.analysis.clientapi.tools.{ListPatternsResponse, ListToolsResponse, ToolsClient}
import com.codacy.analysis.core.model.{AnalyserError, ParameterSpec, PatternSpec, ToolSpec}
import com.codacy.analysis.core.tools.ToolRepository
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.toolRepository.remote.storage.{PatternSpecDataStorage, ToolSpecDataStorage}
import org.log4s.{Logger, getLogger}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class ToolRepositoryRemote(toolsClient: ToolsClient,
                           toolsStorage: String => ToolSpecDataStorage,
                           patternStorage: String => PatternSpecDataStorage)(implicit val ec: ExecutionContext,
                                                                             implicit val mat: Materializer)
    extends ToolRepository {
  private val logger: Logger = getLogger

  private val toolStorageFilename = "tools"
  private val toolStorageInstance = toolsStorage(toolStorageFilename)

  override lazy val list: Either[AnalyserError, Seq[ToolSpec]] = {
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
        toolStorageInstance.save(tools)
        Right(tools)
      case Left(err) =>
        toolStorageInstance.get().toRight(err)
    }
  }

  override def get(uuid: String): Either[AnalyserError, ToolSpec] =
    list.flatMap { toolsSpecs =>
      toolsSpecs.find(_.uuid == uuid) match {
        case None       => Left(AnalyserError.FailedToFindTool(uuid))
        case Some(tool) => Right(tool)
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

  private def downloadPatternsFromApi(
    tool: ToolSpec,
    toolPatternsStorageInstance: PatternSpecDataStorage): Either[AnalyserError, Seq[PatternSpec]] = {
    patternsFromApi(tool) match {
      case Right(patternsFromApi) =>
        logger.info(s"Fetched patterns for ${tool.name} version ${tool.version}")
        toolPatternsStorageInstance.save(patternsFromApi)
        Right(patternsFromApi)
      case Left(err) =>
        logger.error(s"Failed to fetch patterns for ${tool.name} from API: ${err.message}")
        Left(err)
    }
  }

  override def listPatterns(tool: ToolSpec): Either[AnalyserError, Seq[PatternSpec]] = {
    val toolPatternsStorageFilename = s"${tool.uuid}-${tool.version}"
    val toolPatternsStorageInstance = patternStorage(toolPatternsStorageFilename)

    toolPatternsStorageInstance.get() match {
      case Some(patternsFromStorage) =>
        logger.info(s"Using patterns from cache for ${tool.name} version ${tool.version}")
        Right(patternsFromStorage)
      case None =>
        downloadPatternsFromApi(tool, toolPatternsStorageInstance)
    }
  }

  private def toToolSpec(tool: definitions.Tool): ToolSpec = {
    val languages = tool.languages.flatMap(Languages.fromName).to[Set]
    ToolSpec(
      tool.uuid,
      tool.dockerImage,
      tool.enabledByDefault,
      tool.version,
      languages,
      tool.name,
      tool.shortName,
      tool.documentationUrl,
      tool.sourceCodeUrl,
      tool.prefix.getOrElse(""),
      tool.needsCompilation,
      hasConfigFile = tool.configurationFilenames.nonEmpty,
      tool.configurationFilenames.toSet,
      tool.clientSide,
      tool.configurable)
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
