package com.codacy.analysis.cli.utils

import java.util.concurrent.ForkJoinPool

import scala.collection.parallel.{ForkJoinTaskSupport, ParSet}

object SetOps {

  def mapInParallel[A, B](set: Set[A], nrParallel: Option[Int] = Option.empty[Int])(f: A => B): Seq[B] = {
    val setPar: ParSet[A] = set.par
    setPar.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(nrParallel.getOrElse(2)))
    setPar.map(f)(collection.breakOut)
  }

}
