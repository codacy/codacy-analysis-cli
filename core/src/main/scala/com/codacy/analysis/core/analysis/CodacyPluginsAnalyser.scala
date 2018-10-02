package com.codacy.analysis.core.analysis

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.core.model._
import com.codacy.analysis.core.tools.{DuplicationTool, MetricsTool, Tool}
import com.codacy.analysis.core.utils.IOHelper.IOThrowable
import com.codacy.plugins.api.Source
import org.log4s.{Logger, getLogger}
import scalaz.zio.IO

import scala.concurrent.duration._

class CodacyPluginsAnalyser extends Analyser[IOThrowable] {

  private val logger: Logger = getLogger

  override def analyse(tool: Tool,
                       directory: File,
                       files: Set[Path],
                       config: Configuration,
                       timeout: Option[Duration] = Option.empty[Duration]): IOThrowable[Set[ToolResult]] = {
    tool
      .run(directory, files, config, timeout)
      .redeem(
        { e =>
          logger.error(e)(Analyser.Error.ToolExecutionFailure("analysis", tool.name).message)
          IO.fail(e)
        }, { res =>
          logger.info(s"Completed analysis for ${tool.name} with ${res.size} results")
          IO.point(res)
        })
  }

  override def metrics(metricsTool: MetricsTool,
                       directory: File,
                       files: Option[Set[Path]],
                       timeout: Option[Duration] = Option.empty[Duration]): IOThrowable[Set[FileMetrics]] = {

    val srcFiles = files.map(_.map(filePath => Source.File(filePath.toString)))

    metricsTool
      .run(directory, srcFiles, timeout)
      .redeem(
        { e =>
          logger.error(e)(Analyser.Error.ToolExecutionFailure("metrics", metricsTool.name).message)
          IO.fail(e)
        }, { res =>
          logger.info(s"Completed metrics for ${metricsTool.name} with ${res.size} results")
          IO.point(res.to[Set])
        })
  }

  override def duplication(duplicationTool: DuplicationTool,
                           directory: File,
                           files: Set[Path],
                           timeout: Option[Duration] = Option.empty[Duration]): IOThrowable[Set[DuplicationClone]] = {

    duplicationTool
      .run(directory, files, timeout)
      .redeem(
        { e =>
          logger.error(e)(Analyser.Error.ToolExecutionFailure("duplication", duplicationTool.name).message)
          IO.fail(e)
        }, { res =>
          logger.info(s"Completed duplication for ${duplicationTool.name} with ${res.size} results")
          IO.point(res)
        })
  }

}

object CodacyPluginsAnalyser extends AnalyserCompanion[IOThrowable] {

  val name: String = "codacy-plugins"

  private val allToolShortNames = Tool.allToolShortNames

  private val internetToolShortNames = Tool.internetToolShortNames

  override def apply(): Analyser[IOThrowable] = new CodacyPluginsAnalyser()

  object errors {

    def missingTool(tool: String): Analyser.Error = {
      if (internetToolShortNames.contains(tool)) {
        Analyser.Error.ToolNeedsNetwork(tool)
      } else {
        Analyser.Error.NonExistingToolInput(tool, allToolShortNames)
      }
    }
  }

}
