package com.codacy.analysis.core.storage

import scala.util.Try

class DataStorageStub[T] extends DataStorage[T] {
  def get(): Option[Seq[T]] = ???
  def save(values: Seq[T]): Boolean = ???
  def invalidate(): Try[Unit] = ???
}
