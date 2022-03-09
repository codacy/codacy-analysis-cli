package com.codacy.analysis.core.utils

import ch.qos.logback.classic
import ch.qos.logback.classic.Level
import org.slf4j
import org.slf4j.LoggerFactory

object Logger {

  def setLevel(debug: Boolean = false): Unit = {
    val level = if (debug) Level.DEBUG else Level.ERROR
    val root = LoggerFactory.getLogger(slf4j.Logger.ROOT_LOGGER_NAME)
    root match {
      case classicLogger: classic.Logger =>
        classicLogger.setLevel(level)
      case _ =>
    }
  }

}
