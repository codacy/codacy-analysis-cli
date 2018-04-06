package com.codacy.analysis.cli.clients

sealed trait RequestResponse[+A]

final case class SuccessfulResponse[A](value: A) extends RequestResponse[A]

final case class FailedResponse(message: String) extends RequestResponse[Nothing]

object RequestResponse {

  def success[A](a: A): RequestResponse[A] = SuccessfulResponse(a)

  def failure[A](message: String): RequestResponse[A] = FailedResponse(message: String)

  def apply[A](r1: RequestResponse[Seq[A]], r2: RequestResponse[Seq[A]]): RequestResponse[Seq[A]] = {
    r1 match {
      case SuccessfulResponse(v1) =>
        r2 match {
          case SuccessfulResponse(v2) =>
            SuccessfulResponse(v1 ++ v2)
          case f@FailedResponse(_) => f
        }
      case f@FailedResponse(_) => f
    }
  }

}