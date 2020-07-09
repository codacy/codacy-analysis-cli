package com.codacy.analysis.core.clients

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.codacy.analysis.clientapi.definitions
import com.codacy.analysis.clientapi.definitions.{PaginationInfo, Pattern, PatternsListResponse}
import com.codacy.analysis.clientapi.tools.{ListPatternsResponse, ListToolsResponse, ToolsClient}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

case class CodacyTool(uuid: String,
                      name: String,
                      version: String,
                      shortName: String,
                      documentationUrl: Option[String] = None,
                      sourceCodeUrl: Option[String] = None,
                      prefix: Option[String] = None,
                      needsCompilation: Boolean,
                      configFilenames: Seq[String] = Seq.empty,
                      description: Option[String] = None,
                      dockerImage: String,
                      languages: Set[String] = Set.empty,
                      clientSide: Boolean,
                      enabledByDefault: Boolean,
                      configurable: Boolean)

case class CodacyToolPattern(id: String,
                             level: String,
                             category: String,
                             subCategory: Option[String] = None,
                             title: String,
                             shortDescription: Option[String] = None,
                             description: Option[String] = None,
                             enabledByDefault: Boolean,
                             timeToFix: Option[Int] = None,
                             parameters: Seq[CodacyParameter] = Seq.empty)

case class CodacyParameter(name: String, description: Option[String] = None)

class ToolsInformationRepositoryImpl(toolsClient: ToolsClient)(implicit val ec: ExecutionContext,
                                                               implicit val mat: Materializer)
    extends ToolsInformationRepository {
  private def toolResponseToCodacyTool(tool: definitions.Tool) = {
    CodacyTool(
      tool.uuid,
      tool.name,
      tool.version,
      tool.shortName,
      tool.documentationUrl,
      tool.sourceCodeUrl,
      tool.prefix,
      tool.needsCompilation,
      tool.configFilenames,
      tool.description,
      tool.dockerImage,
      tool.languages.toSet,
      tool.clientSide,
      tool.enabledByDefault,
      tool.configurable)
  }

  private def patternResponseToCodacyToolPattern(pattern: definitions.Pattern) = {
    val parameters = pattern.parameters.map { parameter =>
      CodacyParameter(parameter.name, parameter.description)
    }
    CodacyToolPattern(
      pattern.id,
      pattern.level,
      pattern.category,
      pattern.subCategory,
      pattern.title,
      pattern.shortDescription,
      pattern.description,
      pattern.enabledByDefault,
      pattern.timeToFix,
      parameters)
  }

  override lazy val toolsList: Future[Either[String, Set[CodacyTool]]] = toolsClient.listTools().value.map {
    case Right(ListToolsResponse.OK(toolsList)) => Right(toolsList.data.map(toolResponseToCodacyTool).toSet)
    case Right(ListToolsResponse.BadRequest(badRequest)) =>
      Left(s"Call to Codacy API failed with a Bad Request: ${badRequest.message}")
    case Right(ListToolsResponse.InternalServerError(internalServerError)) =>
      Left(s"Call to Codacy API failed with an Internal Server Error: ${internalServerError.message}")
    case Left(Right(error))    => Left(s"Call to Codacy API failed with ${error.status} status code")
    case Left(Left(throwable)) => Left(s"Call to Codacy API failed: ${throwable.getMessage}")
  }

  override def toolPatterns(toolUuid: String): Future[immutable.Seq[CodacyToolPattern]] = {
    val source = PaginatedApiSourceFactory { cursor =>
      toolsClient.listPatterns(toolUuid, cursor).value.map {
        case Right(ListPatternsResponse.OK(PatternsListResponse(data, None | Some(PaginationInfo(None, _, _))))) =>
          (None, data)
        case Right(
            ListPatternsResponse.OK(PatternsListResponse(data, Some(PaginationInfo(newCursor: Some[String], _, _))))) =>
          (newCursor, data)
        case Right(ListPatternsResponse.NotFound(notFoundError))                  => throw new Exception
        case Right(ListPatternsResponse.BadRequest(badRequestErro))               => throw new Exception
        case Right(ListPatternsResponse.InternalServerError(internalServerError)) => throw new Exception
        case Left(Right(e))                                                       => throw new Exception
        case Left(Left(t))                                                        => throw t
      }
    }

    source.runWith(Sink.seq).map(_.map(patternResponseToCodacyToolPattern))
  }
}
