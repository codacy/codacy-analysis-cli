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

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class ToolRepositoryRemote(toolsClient: ToolsClient,
                           toolsStorage: ToolSpecDataStorage,
                           patternStorage: String => PatternSpecDataStorage)(implicit val ec: ExecutionContext,
                                                                             implicit val mat: Materializer)
    extends ToolRepository {

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
        this.toolsStorage.save(tools)
        Right(tools)
      case Left(err) =>
        this.toolsStorage.get().fold[Either[AnalyserError, Seq[ToolSpec]]](Left(err))(Right(_))
    }
  }

  override def get(uuid: String): Either[AnalyserError, ToolSpec] =
    list.flatMap { toolsSpecs =>
      toolsSpecs.find(_.uuid == uuid) match {
        case None       => Left(AnalyserError.FailedToFindTool(uuid))
        case Some(tool) => Right(tool)
      }
    }

  override def listPatterns(toolUuid: String): Either[AnalyserError, Seq[PatternSpec]] = {
    val source = PaginatedApiSourceFactory { cursor =>
      toolsClient.listPatterns(toolUuid, cursor = cursor).value.map {
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

    val patternsF = source.runWith(Sink.seq).map(patterns => Right(patterns.map(toPatternSpec))).recover {
      case e: Exception => Left(AnalyserError.FailedToListPatterns(toolUuid, e.getMessage))
    }

    val result = Await.result(patternsF, 1 minute)
    val patternSpecDataStorage = patternStorage(toolUuid)
    result match {
      case Right(patterns) =>
        patternSpecDataStorage.save(patterns)
        Right(patterns)
      case Left(err) =>
        patternSpecDataStorage.get().fold[Either[AnalyserError, Seq[PatternSpec]]](Left(err))(Right(_))
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

  private def toPatternSpec(pattern: definitions.Pattern): PatternSpec = {
    val parameterSpecs = pattern.parameters.map { parameter =>
      ParameterSpec(parameter.name, parameter.default, parameter.description)
    }

    val languages: Set[Language] = pattern.languages
      .getOrElse(Set.empty)
      .flatMap { languageName =>
        Languages.fromName(languageName)
      }(collection.breakOut)

    PatternSpec(
      pattern.id,
      pattern.level,
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
