package com.codacy.analysis.core.utils

import org.specs2.mutable.Specification

class SetOpsSpec extends Specification {

  "SetOps.mapInParallel" should {
    "actually run in parallel" in {
      val initialTime = System.currentTimeMillis()
      SetOps.mapInParallel(1.to(1000).toSet, Some(1000)) { _ =>
        Thread.sleep(10)
      }
      val finalTime = System.currentTimeMillis()

      val elapsedSeconds = (finalTime - initialTime) / 1000

      elapsedSeconds should beLessThan(9L)
    }
  }
}
