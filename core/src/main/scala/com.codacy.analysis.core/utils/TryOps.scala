package com.codacy.analysis.core.utils

import scala.util.Try

object TryOps {

  implicit class TryOps[A](tryValue: Try[A]) {

    def toRight[L](left: L): Either[L, A] = {
      tryValue.map(Right(_)).getOrElse(Left(left))
    }
  }

}
