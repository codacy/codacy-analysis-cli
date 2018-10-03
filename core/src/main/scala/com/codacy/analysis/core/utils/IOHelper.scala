package com.codacy.analysis.core.utils

import scalaz.zio.IO

import scala.util.{Failure, Success, Try}

object IOHelper {
  type IOThrowable[A] = IO[Throwable, A]

  implicit class IOOps[E, A](io: IO[E, A]) {

    def redeemTry(implicit ev: E =:= Throwable): IO[Nothing, Try[A]] =
      io.redeem(err => IO.point(Failure(err)), results => IO.point(Success(results)))
  }
}
