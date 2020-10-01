package com.codacy.toolRepository.remote

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.codacy.analysis.clientapi.definitions
import com.codacy.analysis.clientapi.definitions.{PaginationInfo, PatternListResponse, ToolListResponse}
import com.codacy.analysis.clientapi.tools.{ListPatternsResponse, ListToolsResponse, ToolsClient}
import com.codacy.analysis.core.model.{ParameterSpec, PatternSpec, ToolSpec}
import com.codacy.analysis.core.tools.ToolRepository
import com.codacy.plugins.api.languages.Languages

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class ToolRepositoryRemote(toolsClient: ToolsClient)(implicit val ec: ExecutionContext, implicit val mat: Materializer)
    extends ToolRepository {

  override def list(): Seq[ToolSpec] = {
    val source = PaginatedApiSourceFactory { cursor =>
      toolsClient.listTools(cursor).value.map {
        case Right(ListToolsResponse.OK(ToolListResponse(data, None | Some(PaginationInfo(None, _, _))))) =>
          (None, data)
        case Right(ListToolsResponse.OK(ToolListResponse(data, Some(PaginationInfo(newCursor: Some[String], _, _))))) =>
          (newCursor, data)
        case Right(ListToolsResponse.BadRequest(badRequest)) =>
          throw new Exception(s"Failed to list tools with a Bad Request: ${badRequest.message}")
        case Right(ListToolsResponse.InternalServerError(internalServerError)) =>
          throw new Exception(s"Failed to list tools with an Internal Server Error: ${internalServerError.message}")
        case Left(Right(error)) => throw new Exception(s"Failed to list tools with ${error.status} status code")
        case Left(Left(throwable)) =>
          throw new Exception(s"Failed to list tools failed: ${throwable.getMessage}", throwable)
      }
    }

    val toolsF = source.runWith(Sink.seq).map(_.map(toToolSpec))
    Await.result(toolsF, 1 minute)
  }

  override def listPatterns(toolUuid: String): Seq[PatternSpec] = {
    val source = PaginatedApiSourceFactory { cursor =>
      toolsClient.listPatterns(toolUuid, cursor = cursor).value.map {
        case Right(ListPatternsResponse.OK(PatternListResponse(data, None | Some(PaginationInfo(None, _, _))))) =>
          (None, data)
        case Right(
              ListPatternsResponse.OK(
                PatternListResponse(data, Some(PaginationInfo(newCursor: Some[String], _, _))))) =>
          (newCursor, data)
        case Right(ListPatternsResponse.NotFound(notFoundError)) =>
          throw new Exception(
            s"Failed to list patterns because tool with UUID $toolUuid does not exist: ${notFoundError.message}")
        case Right(ListPatternsResponse.BadRequest(badRequestError)) =>
          throw new Exception(
            s"Failed to list patterns for tool with UUID $toolUuid with a Bad Request: ${badRequestError.message}")
        case Right(ListPatternsResponse.InternalServerError(internalServerError)) =>
          throw new Exception(
            s"Failed to list patterns for tool with UUID $toolUuid with an Internal Server Error: ${internalServerError.message}")
        case Left(Right(e)) =>
          throw new Exception(
            s"Failed to list patterns for tool with UUID $toolUuid. API returned status code: ${e.status.value}")
        case Left(Left(t)) =>
          throw new Exception(
            s"Failed to list patterns for tool with UUID $toolUuid due to unexpected error: ${t.getMessage}",
            t)
      }
    }

    val patternsF = source.runWith(Sink.seq).map(_.map(toPatternSpec))
    Await.result(patternsF, 1 minute)
  }

  private def toToolSpec(tool: definitions.Tool): ToolSpec = {
    val languages = tool.languages.flatMap(Languages.fromName).to[Set]
    ToolSpec(
      tool.uuid,
      tool.dockerImage,
      tool.enabledByDefault,
      languages,
      tool.name,
      tool.shortName,
      tool.documentationUrl.getOrElse(""), // TODO: Check if this should be an Option too
      tool.sourceCodeUrl.getOrElse(""), // TODO: Check if this should be an Option too
      tool.prefix.getOrElse(""), // TODO: Check if this should be an Option too
      tool.needsCompilation,
      tool.configurationFilenames,
      tool.clientSide,
      tool.configurable)
  }

  private def toPatternSpec(pattern: definitions.Pattern): PatternSpec = {
    val parameterSpecs = pattern.parameters.map { parameter =>
      ParameterSpec(parameter.name, parameter.default, parameter.description)
    }
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
      parameterSpecs)
  }
}
