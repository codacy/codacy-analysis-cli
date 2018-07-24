package com.codacy.analysis.core.utils

object InheritanceOps {

  implicit class InheritanceOps[T](t: T) {
    def to[Super](implicit ev: T <:< Super): Super = t
  }

}
