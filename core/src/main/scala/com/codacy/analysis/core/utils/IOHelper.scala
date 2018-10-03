package com.codacy.analysis.core.utils

import cats.MonadError
import scalaz.zio.IO

import scala.util.{Failure, Success, Try}

object IOHelper {
  type IOThrowable[A] = IO[Throwable, A]

  implicit def ioMonadError[E] = new MonadError[({ type IOE[A] = IO[E, A] })#IOE, E] {
    override def flatMap[A, B](fa: IO[E, A])(f: A => IO[E, B]): IO[E, B] =
      fa.flatMap(f)

    override def tailRecM[A, B](a: A)(f: A => IO[E, Either[A, B]]): IO[E, B] =
      f(a).flatMap {
        case Left(_) =>
          tailRecM(a)(f)
        case Right(b) =>
          IO.point(b)
      }

    override def raiseError[A](e: E): IO[E, A] =
      IO.fail(e)

    override def handleErrorWith[A](fa: IO[E, A])(f: E => IO[E, A]): IO[E, A] =
      fa.redeem(f, IO.point(_): IO[E, A])

    override def pure[A](x: A): IO[E, A] =
      IO.point(x)
  }

  implicit class IOOps[E, A](io: IO[E, A]) {

    def redeemTry(implicit ev: E =:= Throwable): IO[Nothing, Try[A]] =
      io.redeem(err => IO.point(Failure(err)), results => IO.point(Success(results)))
  }
}
