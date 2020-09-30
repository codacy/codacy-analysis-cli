package com.codacy.toolRepository.remote

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

import akka.stream.scaladsl._
import akka.NotUsed

object PaginatedApiSourceFactory {

  def apply[T](doCall: Option[String] => Future[(Option[String], immutable.Seq[T])])(implicit
    ec: ExecutionContext): Source[T, NotUsed] = {

    sealed trait State
    final case class Continue(cursor: Option[String]) extends State
    case object Stop extends State

    Source
      .unfoldAsync[State, immutable.Seq[T]](Continue(None)) {
        case Continue(cursor) =>
          doCall(cursor).map {
            case (some: Some[String], seq) => Some((Continue(some), seq))
            case (None, seq)               => Some((Stop, seq))
          }
        case Stop => Future.successful(None)
      }
      .mapConcat(identity)
  }
}
