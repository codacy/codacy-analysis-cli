package com.codacy.analysis.core.tools

import com.codacy.analysis.core.analysis.Analyser
import com.codacy.analysis.core.clients.{CodacyTool, CodacyToolPattern, ToolsInformationRepository}
import com.codacy.plugins.api.languages.{Language, Languages}
import org.log4s.{Logger, getLogger}

import scala.concurrent.{ExecutionContext, Future}

class ToolCollector(toolsInformationClient: ToolsInformationRepository)(implicit val ec: ExecutionContext) {

  private lazy val toolsFuture: Future[Either[Analyser.Error, Set[CodacyTool]]] =
    toolsInformationClient.toolsList.map(_.left.map(_ => Analyser.Error.FailedToContactCodacyApi))

  private val logger: Logger = getLogger

  def fromNameOrUUID(toolInput: String, languages: Set[Language]): Future[Either[Analyser.Error, Set[Tool]]] = {
    from(toolInput, languages)
  }

  def fromToolUUIDs(toolUuids: Set[String], languages: Set[Language]): Future[Either[Analyser.Error, Set[Tool]]] = {
    if (toolUuids.isEmpty) {
      Future.successful(Left(Analyser.Error.NoActiveToolInConfiguration))
    } else {
      val toolsIdentifiedF =
        toolUuids.foldLeft[Future[Either[Analyser.Error, Set[Tool]]]](Future.successful(Right(Set.empty))) {
          case (accum, uuid) =>
            accum.flatMap { setEither =>
              val newToolsF = from(uuid, languages).map(_.fold({ _ =>
                logger.warn(s"Failed to get tool for uuid:$uuid")
                Set.empty[Tool]
              }, identity))

              newToolsF.map { newTools =>
                setEither.map { oldTools =>
                  newTools ++ oldTools
                }
              }
            }
        }

      toolsIdentifiedF.foreach(_.foreach { toolsIdentified =>
        if (toolsIdentified.size != toolUuids.size) {
          logger.warn("Some tools from remote configuration could not be found locally")
        }
      })

      toolsIdentifiedF
    }
  }

  def fromLanguages(languages: Set[Language]): Future[Either[Analyser.Error, Set[Tool]]] = {
    val toolsAndPatternsF = toolsAndPatternsFiltered(languages = languages)

    toolsAndPatternsF.map { toolsEither =>
      toolsEither.flatMap { toolsAndPatterns =>
        val collectedTools: Set[Tool] = (for {
          (tool, patterns) <- toolsAndPatterns
          languagesToRun = toolLanguages(tool).intersect(languages)
          languageToRun <- languagesToRun
        } yield Tool(tool, patterns, languageToRun))(collection.breakOut)

        if (collectedTools.isEmpty) {
          Left(Analyser.Error.NoToolsFoundForFiles)
        } else {
          Right(collectedTools)
        }
      }
    }
  }

  private def toolsAndPatternsFiltered(
    toolsListF: Future[Either[Analyser.Error, Set[CodacyTool]]] = toolsFuture,
    languages: Set[Language]): Future[Either[Analyser.Error, Seq[(CodacyTool, Seq[CodacyToolPattern])]]] = {
    toolsListF.flatMap {
      case Left(error) => Future.successful(Left(error))
      case Right(tools) =>
        val validTools = tools.filter(toolLanguages(_).intersect(languages).nonEmpty)
        val toolsAndPatterns =
          validTools.foldLeft[Future[Seq[(CodacyTool, Seq[CodacyToolPattern])]]](Future.successful(Seq.empty)) {
            case (accum, tool) =>
              toolsInformationClient.toolPatterns(tool.uuid).flatMap { patterns =>
                accum.map(x => (tool, patterns) +: x)
              }
          }

        toolsAndPatterns.map(Right(_))
    }
  }

  private def from(value: String, languages: Set[Language]): Future[Either[Analyser.Error, Set[Tool]]] = {
    val toolF = find(value).map(_.map(Set(_)))
    toolsAndPatternsFiltered(toolF, languages).map { toolEither =>
      toolEither.map(_.flatMap {
        case (tool, patterns) => toolLanguages(tool).intersect(languages).map(Tool(tool, patterns, _))
      }.toSet)
    }
  }

  private def toolLanguages(codacyTool: CodacyTool): Set[Language] = {
    codacyTool.languages.map(Languages.fromName).map(_.get)
  }

  private def find(value: String): Future[Either[Analyser.Error, CodacyTool]] = {
    toolsFuture.map { toolsEither =>
      toolsEither.flatMap { tools =>
        tools
          .find(p => p.shortName.equalsIgnoreCase(value) || p.uuid.equalsIgnoreCase(value))
          .toRight(Analyser.Error.NonExistingToolInput(value, tools.map(_.shortName)))
      }
    }
  }

}
