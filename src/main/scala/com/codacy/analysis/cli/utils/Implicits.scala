package com.codacy.analysis.cli.utils

object Implicits {

  implicit class OptionOps[T](option: Option[T]) {

    def ifEmpty(f: => Unit): Option[T] = {
      if (option.isEmpty) f
      option
    }
  }

}
