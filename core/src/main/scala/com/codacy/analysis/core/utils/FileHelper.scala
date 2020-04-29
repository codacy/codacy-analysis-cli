package com.codacy.analysis.core.utils

import java.nio.file.Path

import better.files.File
import org.log4s
import org.log4s.getLogger

import scala.util.Try

object FileHelper {

  private val logger: log4s.Logger = getLogger

  def relativePath(filename: String): Path = File.currentWorkingDirectory.relativize(File(filename))

  def countLoc(filename: String): Option[Int] = {
    Try(File(filename).lineIterator).fold(
      { t =>
        logger.error(t)(s"Failed to read file $filename")
        Option.empty[Int]
      },
      { lines =>
        Some(lines.foldLeft(0) {
          case (counter, line) if line.trim.length >= 3 => counter + 1
          case (counter, _)                             => counter
        })
      })
  }
}
