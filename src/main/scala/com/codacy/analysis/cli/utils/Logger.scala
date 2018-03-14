package com.codacy.analysis.cli.utils

import ch.qos.logback.classic
import ch.qos.logback.classic.Level
import org.log4s

object Logger {

  @SuppressWarnings(Array("AsInstanceOf"))
  def withLevel(logger: log4s.Logger, debug: Boolean = false): log4s.Logger = {
    val level = if (debug) Level.DEBUG else Level.OFF
    logger.logger.asInstanceOf[classic.Logger].setLevel(level)
    logger
  }

}
