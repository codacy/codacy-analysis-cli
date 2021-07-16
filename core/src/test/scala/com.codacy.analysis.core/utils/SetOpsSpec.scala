package com.codacy.analysis.core.utils

import org.specs2.mutable.Specification

class SetOpsSpec extends Specification {

  "SetOps.mapInParallel" should {
    "actually run in parallel" in {
      val sleepMillis = 100L
      val parallelism = 20

      val initialTime = System.currentTimeMillis()
      SetOps.mapInParallel(1.to(parallelism).toSet, Some(parallelism)) { _ =>
        Thread.sleep(sleepMillis)
      }
      val finalTime = System.currentTimeMillis()

      val elapsedMillis = finalTime - initialTime
      val expectedMaxMillis = (sleepMillis * parallelism * 0.95).toLong

      elapsedMillis should beLessThan(expectedMaxMillis)
    }
  }
}
