package com.codacy.analysis.cli.utils

import scala.util.{Failure, Success, Try}

object EitherOps {

  def sequenceWithFixedLeftUnit[A](left: A)(eitherIterable: Seq[Either[A, Unit]]): Either[A, Unit] = {
    eitherIterable
      .foldLeft[Either[A, Unit]](Right(())) { (acc, either) =>
        acc.flatMap(_ => either)
      }
      .left
      .map(_ => left)
  }

  def sequenceWithFixedLeft[A, B](left: A)(eitherIterable: Set[Either[A, B]]): Either[A, Set[B]] = {
    eitherIterable
      .foldLeft[Either[A, Set[B]]](Right(Set.empty)) { (acc, either) =>
        acc.flatMap { bSeq =>
          either.map(b => bSeq ++ Set(b))
        }
      }
      .left
      .map(_ => left)
  }

  def fromTry[A](tryValue: Try[A]): Either[String, A] = {
    tryValue match {
      case Success(value) => Right(value)
      case Failure(err)   => Left(err.getMessage)
    }
  }

}
