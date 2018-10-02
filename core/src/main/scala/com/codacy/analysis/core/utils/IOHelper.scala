package com.codacy.analysis.core.utils

import cats.MonadError
import scalaz.zio.IO

object IOHelper {
  type IOThrowable[A] = IO[Throwable, A]

  val ioExceptionMonadError: MonadError[IOThrowable, Throwable] = new MonadError[IOThrowable, Throwable] {
    override def raiseError[A](e: Throwable): IOThrowable[A] =
      IO.fail(e)

    override def handleErrorWith[A](fa: IOThrowable[A])(f: Throwable => IOThrowable[A]): IOThrowable[A] =
      fa.redeem(f, IO.point(_): IOThrowable[A])

    override def pure[A](x: A): IOThrowable[A] =
      IO.point(x)

    override def flatMap[A, B](fa: IOThrowable[A])(f: A => IOThrowable[B]): IOThrowable[B] =
      fa.flatMap(f)

    override def tailRecM[A, B](a: A)(f: A => IOThrowable[Either[A, B]]): IOThrowable[B] = {
      f(a).flatMap {
        case Left(_) =>
          tailRecM(a)(f)
        case Right(b) =>
          IO.point(b)
      }
    }
  }
}
