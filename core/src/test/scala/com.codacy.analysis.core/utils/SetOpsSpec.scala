package com.codacy.analysis.core.utils

import org.specs2.mutable.Specification

class SetOpsSpec extends Specification {

  "SetOps.mapInParallel" should {
    "actually run in parallel" in {
      val parallelism = 10

      val threadsIds = SetOps.mapInParallel(1.to(parallelism).toSet, Some(parallelism)) { _ =>
        // If the function terminates too fast the thread pool reuses the same threads
        Thread.sleep(100)

        Thread.currentThread().getId()
      }

      threadsIds.distinct should haveSize(parallelism)
    }
  }
}
