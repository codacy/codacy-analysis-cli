package com.codacy.analysis.cli.toolRepository

import com.codacy.analysis.core.tools.ToolRepository
import com.codacy.toolRepository.plugins.ToolRepositoryPlugins
import com.codacy.toolRepository.remote.ToolRepositoryRemote

object ToolRepositoryFactory {

  def build(fetchRemoteTools: Boolean): ToolRepository =
    if (fetchRemoteTools) {
      new ToolRepositoryRemote()
    } else {
      new ToolRepositoryPlugins()
    }
}
