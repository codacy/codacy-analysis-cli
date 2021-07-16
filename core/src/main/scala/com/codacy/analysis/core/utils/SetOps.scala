package com.codacy.analysis.core.utils

import java.util.concurrent.ForkJoinPool

import scala.collection.parallel.ForkJoinTaskSupport
import scala.collection.parallel.immutable.ParSet

object SetOps {

  def mapInParallel[A, B](set: Set[A], nrParallel: Option[Int] = Option.empty[Int])(f: A => B): Seq[B] = {
    val setPar: ParSet[A] = set.par
    val forkJoinPool = new ForkJoinPool(nrParallel.getOrElse(2))
    setPar.tasksupport = new ForkJoinTaskSupport(forkJoinPool)
    val result = setPar.map(f)
    forkJoinPool.shutdown()
    result.seq.toSeq
  }

}
