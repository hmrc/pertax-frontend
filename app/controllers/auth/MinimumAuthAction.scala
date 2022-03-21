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
import config.ConfigDecorator
import controllers.auth.requests.{AuthenticatedRequest, SelfAssessmentEnrolment, SelfAssessmentStatus}
import controllers.routes
import models.UserName
import play.api.Configuration
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.{Name, v2, ~}
import uk.gov.hmrc.domain
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class MinimumAuthAction @Inject() (
  val authConnector: AuthConnector,
  configuration: Configuration,
  configDecorator: ConfigDecorator,
  sessionAuditor: SessionAuditor,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends AuthAction with AuthorisedFunctions {

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {

    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    authorised(ConfidenceLevel.L50)
      .retrieve(
        Retrievals.nino and
          Retrievals.allEnrolments and
          Retrievals.credentials and
          Retrievals.confidenceLevel and
          Retrievals.name and
          Retrievals.trustedHelper and
          Retrievals.profile
      ) {
        case nino ~ Enrolments(enrolments) ~ Some(credentials) ~ confidenceLevel ~ name ~ trustedHelper ~ profile =>
          val saEnrolment = enrolments.find(_.key == "IR-SA").flatMap { enrolment =>
            enrolment.identifiers
              .find(id => id.key == "UTR")
              .map(key => SelfAssessmentEnrolment(SaUtr(key.value), SelfAssessmentStatus.fromString(enrolment.state)))
          }

          val trimmedRequest: Request[A] = request
            .map {
              case AnyContentAsFormUrlEncoded(data) =>
                AnyContentAsFormUrlEncoded(data.map { case (key, vals) =>
                  (key, vals.map(_.trim))
                })
              case b => b
            }
            .asInstanceOf[Request[A]]

          val authenticatedRequest =
            AuthenticatedRequest[A](
              nino.map(domain.Nino),
              saEnrolment,
              credentials,
              confidenceLevel,
              Some(UserName(name.getOrElse(Name(None, None)))),
              trustedHelper,
              profile,
              enrolments,
              trimmedRequest
            )

          for {
            result        <- block(authenticatedRequest)
            updatedResult <- sessionAuditor.auditOnce(authenticatedRequest, result)
          } yield updatedResult

        case _ => throw new RuntimeException("Can't find credentials for user")
      }
  } recover {
    case _: NoActiveSession => Results.Redirect(routes.PublicController.sessionTimeout).withNewSession

    case _: InsufficientEnrolments => throw InsufficientEnrolments("")
  }

  override def parser: BodyParser[AnyContent] = cc.parsers.defaultBodyParser

  override protected def executionContext: ExecutionContext = cc.executionContext
}
