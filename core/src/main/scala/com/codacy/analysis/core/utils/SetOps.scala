package com.codacy.analysis.core.utils

import java.util.concurrent.ForkJoinPool

import scala.collection.parallel.ForkJoinTaskSupport
import scala.collection.parallel.immutable.ParSet
import scala.reflect.ClassTag

object SetOps {

  def mapInParallel[A, B](set: Set[A], nrParallel: Option[Int] = Option.empty[Int])(f: A => B): Seq[B] = {
    val setPar: ParSet[A] = set.par
    setPar.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(nrParallel.getOrElse(2)))
    setPar.map(f)(collection.breakOut)
  }

  implicit class SetOps[T](set: Set[T]) {

    def partitionSubtypes[A <: T: ClassTag, B <: T: ClassTag, C <: T: ClassTag]: (Set[A], Set[B], Set[C]) = {
      set.foldLeft((Set.empty[A], Set.empty[B], Set.empty[C])) {
        case ((aSet, bSet, cSet), t) =>
          t match {
            case a: A => (aSet + a, bSet, cSet)
            case b: B => (aSet, bSet + b, cSet)
            case c: C => (aSet, bSet, cSet + c)
            case _    => (aSet, bSet, cSet)
          }
      }
    }
  }

}
