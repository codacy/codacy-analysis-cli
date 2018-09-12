package com.codacy.analysis.core.utils

object MapOps {

  def merge[A, B](map1: Map[A, Set[B]], map2: Map[A, Set[B]]): Map[A, Set[B]] = {
    map1.foldLeft[Map[A, Set[B]]](map2) {
      case (accum, (key, value)) =>
        val mergedValue = accum.get(key).fold(value)(other => other ++ value)
        accum + (key -> mergedValue)
    }
  }
}
