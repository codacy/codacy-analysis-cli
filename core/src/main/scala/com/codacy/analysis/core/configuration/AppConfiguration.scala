package com.codacy.analysis.core.configuration

import com.typesafe.config.{Config, ConfigFactory}

object AppConfiguration {

  private val conf: Config = ConfigFactory.load()

  val batchSize: Int = conf.getInt("batchSize");

}
