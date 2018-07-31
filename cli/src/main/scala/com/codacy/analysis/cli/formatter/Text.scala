package com.codacy.analysis.cli.formatter

import java.io.PrintStream
import java.nio.file.Path

import com.codacy.analysis.core.model._
import com.codacy.plugins.api.results
import com.codacy.plugins.duplication.api.DuplicationCloneFile

object Text extends FormatterCompanion {
  val name: String = "text"

  def apply(stream: PrintStream): Formatter = new Text(stream)
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

  def add(element: Result): Unit = {
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
        fileMetricsMessage(fileMetrics).map(stream.println)
        stream.flush()
    }
  }

  private def fileMetricsMessage(fileMetrics: FileMetrics): List[String] = {
    def prettyNamedValue(name: String, value: Int): String =
      s"$name = $value"

    val fileMetricsList = List(
      fileMetrics.complexity.map(prettyNamedValue("cyclomatic complexity", _)),
      fileMetrics.loc.map(prettyNamedValue("lines of code", _)),
      fileMetrics.cloc.map(prettyNamedValue("commented lines of code", _)),
      fileMetrics.nrMethods.map(prettyNamedValue("number of methods", _)),
      fileMetrics.nrClasses.map(prettyNamedValue("number of classes", _)))

    val fileMetricsValues = fileMetricsList.collect {
      case Some(namedValue) => namedValue
    }

    if (fileMetricsValues.isEmpty) {
      List(s"No metrics found on file ${fileMetrics.filename}")
    } else {
      fileMetricsValues.map { fileMetricValue =>
        s"Found $fileMetricValue on file ${fileMetrics.filename}"
      }

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
    val cloneFoundColored = Console.MAGENTA + "Clone" + Console.RESET
    val duplicatedFilesMsg = files.map { file =>
      s"${file.filePath}, from line ${file.startLine} to ${file.endLine}"
    }.mkString("; ")
    val message =
      s"There are $nrLines duplicated lines with $nrTokens tokens on these files: $duplicatedFilesMsg"
    s"Found [$cloneFoundColored] $message"
  }

  private def levelColor(level: results.Result.Level): String = {
    level match {
      case results.Result.Level.Info => Console.BLUE
      case results.Result.Level.Warn => Console.YELLOW
      case results.Result.Level.Err  => Console.RED
    }
  }

}
