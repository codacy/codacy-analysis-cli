package com.codacy.analysis.core.utils

object EitherOps {

  def sequenceUnitWithFixedLeft[A](left: A)(eitherSeq: Seq[Either[A, Unit]]): Either[A, Unit] = {
    eitherSeq
      .foldLeft[Either[A, Unit]](Right(())) { (acc, either) =>
        acc.flatMap(_ => either)
      }
      .left
      .map(_ => left)
  }

  def sequenceWithFixedLeft[A, B](left: A)(eitherSet: Set[Either[A, B]]): Either[A, Set[B]] = {
    eitherSet
      .foldLeft[Either[A, Set[B]]](Right(Set.empty)) { (acc, either) =>
        acc.flatMap { bSeq =>
          either.map(b => bSeq ++ Set(b))
        }
      }
      .left
      .map(_ => left)
  }

  def sequenceFoldingLeft[A](eithers: Seq[Either[A, Unit]])(op: (A, A) => A): Either[A, Unit] = {
    eithers.fold(Right[A, Unit](())) {
      case (Left(error1), Left(error2)) =>
        Left(op(error1, error2))
      case (Right(_), Left(error2)) =>
        Left(error2)
      case (Left(error1), Right(_)) =>
        Left(error1)
      case (Right(_), Right(_)) =>
        Right(())
    }
  }

}
