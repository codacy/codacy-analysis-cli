package com.codacy.analysis.core.utils

import com.codacy.analysis.core.files.FilesTarget
import com.codacy.plugins.api.languages.{Language, Languages}
import org.log4s.getLogger

object LanguagesHelper {

  private val logger: org.log4s.Logger = getLogger

  def fromFileTarget(filesTarget: FilesTarget, languageCustomExtensions: Map[Language, Set[String]]): Set[Language] = {
    for {
      path <- filesTarget.readableFiles
      extensionsList = languageCustomExtensions.mapValues(_.toList).toList
      language <- Languages.forPath(path.toString, extensionsList).orElse {
        logger.info(s"No language found for ${path.toString}")
        None
      }
    } yield language
  }
}
