package com.codacy.analysis.core.utils

import cats.MonadError
import scalaz.zio.IO

import scala.util.{Failure, Success, Try}

object IOHelper {
  type IOThrowable[A] = IO[Throwable, A]

  def fromEither[E, A](either: => Either[E, A]) = {
    IO.point(()).flatMap(_ => IO.fromEither(either))
  }

  def fromTry[A](try_ : => scala.util.Try[A]): IO[Throwable, A] = {
    IO.point(()).flatMap(_ => IO.fromTry(try_))
  }

  implicit val ioExceptionMonadError: MonadError[IOThrowable, Throwable] = new MonadError[IOThrowable, Throwable] {
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

  implicit class IOOps[E, A](io: IO[E, A]) {

    def redeemTry(implicit ev: E =:= Throwable): IO[Nothing, Try[A]] =
      io.redeem(err => IO.point(Failure(err)), results => IO.point(Success(results)))
  }
}
