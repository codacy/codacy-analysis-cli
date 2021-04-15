package com.codacy.analysis.core.clients

sealed trait Credentials {
  val baseUrl: String
}

final case class ProjectToken(token: String, baseUrl: String) extends Credentials

final case class APIToken(token: String,
                          baseUrl: String,
                          provider: OrganizationProvider.Value,
                          userName: UserName,
                          projectName: ProjectName)
    extends Credentials

object OrganizationProvider extends Enumeration {
  val manual, gh, bb, ghe, bbe, gl, gle = Value
}

final case class UserName(private val userName: String) extends AnyVal {
  override def toString: String = userName
}

final case class ProjectName(private val projectName: String) extends AnyVal {
  override def toString: String = projectName
}
