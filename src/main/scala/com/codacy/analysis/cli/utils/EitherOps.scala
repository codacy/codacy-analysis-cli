package com.codacy.analysis.cli.utils

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

}
