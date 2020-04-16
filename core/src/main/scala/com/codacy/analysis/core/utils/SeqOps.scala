package com.codacy.analysis.core.utils

import scala.reflect.ClassTag

object SeqOps {

  implicit class SeqOps[T](seq: Seq[T]) {

    def partitionSubtypes[A <: T: ClassTag, B <: T: ClassTag, C <: T: ClassTag]: (Seq[A], Seq[B], Seq[C]) = {
      seq.foldLeft((Seq.empty[A], Seq.empty[B], Seq.empty[C])) {
        case ((aSeq, bSeq, cSeq), t) =>
          t match {
            case a: A => (aSeq :+ a, bSeq, cSeq)
            case b: B => (aSeq, bSeq :+ b, cSeq)
            case c: C => (aSeq, bSeq, cSeq :+ c)
            case _    => (aSeq, bSeq, cSeq)
          }
      }
    }
  }
}
