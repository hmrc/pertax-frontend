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

import _root_.util.{AuditServiceTools, EnrolmentsHelper}
import com.google.inject.Inject
import controllers.auth.UserSessionAuditEvent.writes
import controllers.auth.requests.AuthenticatedRequest
import play.api.Logging
import play.api.libs.json._
import play.api.mvc.Result
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.{AffinityGroup, ConfidenceLevel, Enrolment}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.{Failure, Success}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.{ExecutionContext, Future}

private[auth] class SessionAuditor @Inject() (auditConnector: AuditConnector, enrolmentsHelper: EnrolmentsHelper)(
  implicit ec: ExecutionContext
) extends AuditTags
    with Logging {

  val sessionKey = "sessionAudited"
  val auditType  = "user-session-visit"

  def auditOnce[A](request: AuthenticatedRequest[A], result: Result)(implicit hc: HeaderCarrier): Future[Result] =
    request.session.get(sessionKey) match {
      case None =>
        logger.info(request.profile.toString)

        val eventDetail = userSessionAuditEventFromRequest(request)

        val sendAuditEvent = auditConnector
          .sendExtendedEvent(
            ExtendedDataEvent(
              auditSource = AuditServiceTools.auditSource,
              auditType = auditType,
              detail = Json.toJson(writes(eventDetail)),
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

  def userSessionAuditEventFromRequest(request: AuthenticatedRequest[_]): UserSessionAuditEvent = {
    val nino            = request.nino
    val credentials     = request.credentials
    val confidenceLevel = request.confidenceLevel
    val name            = request.name map (_.toString)
    val saUtr           = enrolmentsHelper.selfAssessmentStatus(request.enrolments, request.trustedHelper) map (_.saUtr)
    val enrolments      = request.enrolments
    val affinityGroup   = request.affinityGroup

    UserSessionAuditEvent(nino, credentials, confidenceLevel, name, saUtr, enrolments, affinityGroup)

  }
}

case class UserSessionAuditEvent(
  nino: Option[Nino],
  credentials: Credentials,
  confidenceLevel: ConfidenceLevel,
  name: Option[String],
  saUtr: Option[SaUtr],
  allEnrolments: Set[Enrolment],
  affinityGroup: Option[AffinityGroup]
)

object UserSessionAuditEvent {
  implicit val credentialsFormats: OFormat[Credentials] = Json.format[Credentials]
  implicit val formats: Format[UserSessionAuditEvent]   = Json.format[UserSessionAuditEvent]

  def removeNulls(jsObject: JsObject): JsObject =
    JsObject(jsObject.fields.collect {
      case (s, j: JsObject)            =>
        (s, removeNulls(j))
      case other if other._2 != JsNull =>
        other
    })

  def writes(model: UserSessionAuditEvent): JsObject = {
    implicit val credentialsFormats: OFormat[Credentials] = Json.format[Credentials]
    val flattenEnrolments                                 = model.allEnrolments.flatMap { enrolment =>
      val key = enrolment.key
      enrolment.identifiers.map { identifier =>
        s"$key-${identifier.key}" -> Json.toJson(identifier.value)
      }
    }

    removeNulls(
      flattenEnrolments.foldLeft(
        Json.obj(
          "nino"            -> model.nino,
          "affinityGroup"   -> model.affinityGroup.fold("None")(_.toString),
          "credentials"     -> model.credentials,
          "confidenceLevel" -> model.confidenceLevel,
          "name"            -> model.name,
          "saUtr"           -> model.saUtr,
          "allEnrolments"   -> model.allEnrolments
        )
      ) { (initialObject, currElement) =>
        val newElement = currElement match {
          case (jsObject, element) =>
            jsObject -> element
        }
        initialObject + newElement
      }
    )
  }
}
