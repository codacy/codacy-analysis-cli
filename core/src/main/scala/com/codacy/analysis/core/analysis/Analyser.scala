package com.codacy.analysis.core.analysis

import java.nio.file.Path

import better.files.File
import com.codacy.analysis.core.model.{Configuration, DuplicationClone, FileMetrics, ToolResult}
import com.codacy.analysis.core.tools.{DuplicationTool, MetricsTool, Tool}
import org.log4s.{Logger, getLogger}

import scala.concurrent.duration.Duration
import scala.util.Try

trait Analyser {

  def analyse(tool: Tool,
              directory: File,
              files: Set[Path],
              config: Configuration,
              tmpDirectory: Option[File],
              timeout: Option[Duration] = Option.empty[Duration],
              maxToolMemory: Option[String] = None): Try[Set[ToolResult]]

  def metrics(metricsTool: MetricsTool,
              directory: File,
              files: Option[Set[Path]],
              tmpDirectory: Option[File],
              timeout: Option[Duration] = Option.empty[Duration],
              maxToolMemory: Option[String] = None): Try[Set[FileMetrics]]

  def duplication(duplicationTool: DuplicationTool,
                  directory: File,
                  files: Set[Path],
                  tmpDirectory: Option[File],
                  timeout: Option[Duration] = Option.empty[Duration],
                  maxToolMemory: Option[String] = None): Try[Set[DuplicationClone]]

}
