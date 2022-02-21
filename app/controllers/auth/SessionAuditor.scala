/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers.auth

import com.google.inject.Inject
import controllers.auth.SessionAuditor._
import controllers.auth.requests.AuthenticatedRequest
import play.api.Logging
import play.api.libs.json.{Format, Json}
import play.api.mvc.Result
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.{Failure, Success}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import util.AuditServiceTools

import scala.concurrent.{ExecutionContext, Future}

private[auth] class SessionAuditor @Inject() (auditConnector: AuditConnector)(implicit ec: ExecutionContext)
    extends AuditTags with Logging {

  def auditOnce[A](request: AuthenticatedRequest[A], result: Result)(implicit hc: HeaderCarrier): Future[Result] =
    request.session.get(sessionKey) match {
      case None =>
        logger.info(request.profile.toString)

        val eventDetail = UserSessionAuditEvent(request)

        val sendAuditEvent = auditConnector
          .sendExtendedEvent(
            ExtendedDataEvent(
              auditSource = AuditServiceTools.auditSource,
              auditType = auditType,
              detail = Json.toJson(eventDetail),
              tags = buildTags(request)
            )
          )
          .recover { case e: Exception =>
            logger.warn(s"Unable to audit: ${e.getMessage}")
            Failure("UserSessionAuditor.auditOncePerSession exception occurred whilst auditing", Some(e))
          }

        sendAuditEvent.map {
          case Success => result.addingToSession(sessionKey -> "true")(request)
          case _       => result
        }

      case _ => Future.successful(result)
    }
}

object SessionAuditor {

  val sessionKey = "sessionAudited"
  val auditType = "user-session-visit"

  case class UserSessionAuditEvent(
    nino: Option[Nino],
    credentials: Credentials,
    confidenceLevel: ConfidenceLevel,
    name: Option[String],
    saUtr: Option[SaUtr],
    allEnrolments: Set[Enrolment]
  )

  object UserSessionAuditEvent {

    def apply[A](request: AuthenticatedRequest[A]): UserSessionAuditEvent = {
      val nino = request.nino
      val credentials = request.credentials
      val confidenceLevel = request.confidenceLevel
      val name = request.name map (_.toString)
      val saUtr = request.saEnrolment map (_.saUtr)
      val enrolments = request.enrolments

      UserSessionAuditEvent(nino, credentials, confidenceLevel, name, saUtr, enrolments)
    }

    implicit val credentialsFormats = Json.format[Credentials]
    implicit val formats: Format[UserSessionAuditEvent] = Json.format[UserSessionAuditEvent]
  }

}
