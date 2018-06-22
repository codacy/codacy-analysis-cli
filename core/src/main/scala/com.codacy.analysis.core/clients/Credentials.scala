package com.codacy.analysis.core.clients

sealed trait Credentials {
  val baseUrl: Option[String]
}

final case class ProjectToken(token: String, baseUrl: Option[String] = Option.empty[String]) extends Credentials
final case class APIToken(token: String,
                          baseUrl: Option[String] = Option.empty[String],
                          userName: UserName,
                          projectName: ProjectName)
    extends Credentials

final case class UserName(private val userName: String) extends AnyVal {
  override def toString: String = userName
}
final case class ProjectName(private val projectName: String) extends AnyVal {
  override def toString: String = projectName
}
