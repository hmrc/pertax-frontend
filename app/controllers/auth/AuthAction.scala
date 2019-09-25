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
import connectors.NewPertaxAuthConnector
import controllers.auth.requests.AuthenticatedRequest
import controllers.routes
import play.api.Configuration
import play.api.mvc._
import uk.gov.hmrc.domain
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.frontend.binders.SafeRedirectUrl

import scala.concurrent.{ExecutionContext, Future}

class AuthActionImpl @Inject()(val authConnector: NewPertaxAuthConnector, configuration: Configuration)(
  implicit ec: ExecutionContext)
    extends AuthAction with AuthorisedFunctions {

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {

    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    authorised((ConfidenceLevel.L200 and Nino(hasNino = true)) or Enrolment("IR-SA"))
      .retrieve(Retrievals.nino and Retrievals.authorisedEnrolments and Retrievals.credentials) {
        case nino ~ Enrolments(enrolments) ~ Some(credentials) =>
          val saUtr = enrolments.find(_.key == "IR-SA").flatMap { enrolment =>
            enrolment.identifiers.find(id => id.key == "UTR").map(_.value)
          }
          val isVerify = credentials.providerType != "GovernmmentGateway"
          block(AuthenticatedRequest(nino.map(domain.Nino), saUtr.map(domain.SaUtr), isVerify, request))
        case _ => throw new RuntimeException("Can't find credentials for user")
      }
  } recover {
    case _: NoActiveSession => Results.Redirect(routes.PublicController.sessionTimeout()).withNewSession

    case _: InsufficientConfidenceLevel =>
      Results.Redirect(
        controllers.routes.ApplicationController
          .uplift(redirectUrl = Some(SafeRedirectUrl(request.uri))))

    //TODO: handle this nicerly
    case _: InsufficientEnrolments => throw InsufficientEnrolments("")
  }
}

@ImplementedBy(classOf[AuthActionImpl])
trait AuthAction extends ActionBuilder[AuthenticatedRequest] with ActionFunction[Request, AuthenticatedRequest]
