package com.codacy.analysis.core.tools

import com.codacy.plugins.runners.BinaryDockerRunner

private[tools] object ToolsShared {

  def dockerConfig(maxToolMemory: Option[String]) = {
    maxToolMemory match {
      case v: Some[String] => BinaryDockerRunner.Config(containerMemoryLimit = v)
      case None            => BinaryDockerRunner.Config() // Use the codacy-plugins default when not set.
    }
  }
}
