package com.codacy.analysis.cli.clients

import utils.PluginHelper

object CodacyPlugins {

  def getPluginUuidByShortName(shortName: String): Option[String] = PluginHelper.dockerEnterprisePlugins.collectFirst {
    case plugin if plugin.shortName == shortName => plugin.uuid
  }

}
