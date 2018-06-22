package com.codacy.analysis.core.utils

object Implicits {

  implicit class OptionOps[T](option: Option[T]) {

    def ifEmpty(f: => Unit): Option[T] = {
      if (option.isEmpty) f
      option
    }
  }

}
