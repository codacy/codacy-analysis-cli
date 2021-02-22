package com.codacy.analysis.cli.formatter

import java.io.PrintStream
import java.nio.file.Path

import better.files.File
import com.codacy.analysis.core.model._
import com.codacy.plugins.api.duplication.DuplicationCloneFile
import com.codacy.plugins.api.{PatternDescription, results}

object Text extends FormatterCompanion {
  override val name: String = "text"

  override def apply(printStream: PrintStream, executionDirectory: File, ghCodeScanningCompat: Boolean): Formatter =
    new Text(printStream)
}

private[formatter] class Text(val stream: PrintStream) extends Formatter {

  override def begin(): Unit = {
    stream.println("Starting analysis ...")
    stream.flush()
  }

  override def end(): Unit = {
    stream.println("Analysis complete")
    stream.flush()
  }

  override def addAll(toolSpecification: Option[com.codacy.plugins.api.results.Tool.Specification],
                      patternDescriptions: Set[PatternDescription],
                      toolPrefix: Option[String],
                      elements: Seq[Result]): Unit = elements.foreach(add)

  private def add(element: Result): Unit = {
    element match {
      case Issue(patternId, filename, message, level, category, location) =>
        stream.println(prettyMessage(patternId, filename, message, level, category, location))
        stream.flush()
      case FileError(filename, message) =>
        stream.println(s"Found $message in $filename")
        stream.flush()
      case DuplicationClone(_, nrTokens, nrLines, files) =>
        stream.println(prettyMessage(nrTokens, nrLines, files))
        stream.flush()
      case fileMetrics: FileMetrics =>
        stream.println(prettyMessage(fileMetrics))
        stream.flush()
    }
  }

  private def prettyMessage(fileMetrics: FileMetrics): String = {
    val fileMetricsValues = List(
      fileMetrics.complexity.map(complexityNum => s"  CC - $complexityNum"),
      fileMetrics.loc.map(loc => s"  LOC - $loc"),
      fileMetrics.cloc.map(cloc => s"  CLOC - $cloc"),
      fileMetrics.nrMethods.map(nrMethods => s"  #methods - $nrMethods"),
      fileMetrics.nrClasses.map(nrClasses => s"  #classes - $nrClasses")).collect {
      case Some(namedValue) => namedValue
    }

    val coloredMetricsFound = Console.MAGENTA + "Metrics" + Console.RESET
    val boldFileName = s"${Console.BOLD}${fileMetrics.filename}${Console.RESET}"

    if (fileMetricsValues.isEmpty) {
      s"No [$coloredMetricsFound] found in $boldFileName."
    } else {
      s"Found [$coloredMetricsFound] in $boldFileName:\n${fileMetricsValues.mkString("\n")}"
    }
  }

  private def prettyMessage(patternId: results.Pattern.Id,
                            filename: Path,
                            message: Issue.Message,
                            level: results.Result.Level,
                            category: Option[results.Pattern.Category],
                            location: Location): String = {
    val categoryColored = Console.YELLOW + category.fold("")(c => s"/${c.toString}") + Console.RESET
    val levelColored = levelColor(level) + level + Console.RESET
    val patternColored = Console.BOLD + patternId + Console.RESET
    s"Found [$levelColored$categoryColored] `$message` in $filename:$location ($patternColored)"
  }

  private def prettyMessage(nrTokens: Int, nrLines: Int, files: Set[DuplicationCloneFile]): String = {
    val coloredCloneFound = Console.CYAN + "Clone" + Console.RESET
    val duplicatedFilesMsg = files
      .groupBy(_.filePath)
      .map {
        case (filePath, cloneFiles) =>
          val lineNumbers =
            cloneFiles.map(cloneFile => s"    l. ${cloneFile.startLine} - ${cloneFile.endLine}").mkString("\n")
          s"  ${Console.BOLD}$filePath${Console.RESET}\n$lineNumbers"
      }
      .mkString("\n")
    s"Found [$coloredCloneFound] $nrLines duplicated lines with $nrTokens tokens:\n$duplicatedFilesMsg"
  }

  private def levelColor(level: results.Result.Level): String = {
    level match {
      case results.Result.Level.Info => Console.BLUE
      case results.Result.Level.Warn => Console.YELLOW
      case results.Result.Level.Err  => Console.RED
    }
  }

}
