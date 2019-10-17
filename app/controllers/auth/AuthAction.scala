/*
 * Copyright 2019 HM Revenue & Customs
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

import com.google.inject.{ImplementedBy, Inject}
import config.ConfigDecorator
import connectors.NewPertaxAuthConnector
import controllers.auth.requests.{AuthenticatedRequest, SelfAssessmentEnrolment}
import controllers.routes
import models.UserName
import play.api.Configuration
import play.api.mvc.Results.Redirect
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.{Name, ~}
import uk.gov.hmrc.domain
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.frontend.binders.SafeRedirectUrl

import scala.concurrent.{ExecutionContext, Future}

class AuthActionImpl @Inject()(
  val authConnector: NewPertaxAuthConnector,
  configuration: Configuration,
  configDecorator: ConfigDecorator)(implicit ec: ExecutionContext)
    extends AuthAction with AuthorisedFunctions {

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {

    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
    authorised((Enrolment("IR-SA") or Nino(hasNino = true)) and ConfidenceLevel.L200)
      .retrieve(
        Retrievals.nino and
          Retrievals.allEnrolments and
          Retrievals.credentials and
          Retrievals.confidenceLevel and
          Retrievals.name and
          Retrievals.loginTimes) {
        case nino ~ Enrolments(enrolments) ~ Some(credentials) ~ confidenceLevel ~ name ~ logins =>
          val saEnrolment = enrolments.find(_.key == "IR-SA").flatMap { enrolment =>
            enrolment.identifiers
              .find(id => id.key == "UTR")
              .map(key => SelfAssessmentEnrolment(SaUtr(key.value), enrolment.state))
          }

          val trimmedRequest: Request[A] = request
            .map {
              case AnyContentAsFormUrlEncoded(data) =>
                AnyContentAsFormUrlEncoded(data.map {
                  case (key, vals) => (key, vals.map(_.trim))
                })
              case b => b
            }
            .asInstanceOf[Request[A]]

          block(
            AuthenticatedRequest[A](
              nino.map(domain.Nino),
              saEnrolment,
              credentials.providerType,
              confidenceLevel,
              Some(UserName(name.getOrElse(Name(None, None)))),
              logins.previousLogin,
              trimmedRequest
            ))
        case _ => throw new RuntimeException("Can't find credentials for user")
      }
  } recover {
    case _: NoActiveSession => Results.Redirect(routes.PublicController.sessionTimeout()).withNewSession

    case _: InsufficientConfidenceLevel =>
      Redirect(
        configDecorator.identityVerificationUpliftUrl,
        Map(
          "origin"          -> Seq(configDecorator.origin),
          "confidenceLevel" -> Seq(ConfidenceLevel.L200.toString),
          "completionURL" -> Seq(
            configDecorator.pertaxFrontendHost + routes.ApplicationController.showUpliftJourneyOutcome(
              Some(SafeRedirectUrl(request.uri)))),
          "failureURL" -> Seq(
            configDecorator.pertaxFrontendHost + routes.ApplicationController.showUpliftJourneyOutcome(
              Some(SafeRedirectUrl(request.uri))))
        )
      )

    //TODO: handle this nicerly
    case _: InsufficientEnrolments => throw InsufficientEnrolments("")
  }
}

@ImplementedBy(classOf[AuthActionImpl])
trait AuthAction extends ActionBuilder[AuthenticatedRequest] with ActionFunction[Request, AuthenticatedRequest]
