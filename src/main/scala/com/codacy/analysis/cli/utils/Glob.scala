package com.codacy.analysis.cli.utils

import java.nio.file.Paths

import better.files.File

case class Glob(value: String) extends AnyVal {
  override def toString: String = value

  def matches(file: String): Boolean = {
    val matcher = File(file).fileSystem.getPathMatcher(s"glob:$value")
    matcher.matches(Paths.get(file))
  }
}
