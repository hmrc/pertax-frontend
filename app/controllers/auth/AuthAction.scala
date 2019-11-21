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
import connectors.PertaxAuthConnector
import controllers.auth.requests.{AuthenticatedRequest, SelfAssessmentEnrolment, SelfAssessmentStatus}
import controllers.routes
import models.UserName
import play.api.Configuration
import play.api.mvc.Results.Redirect
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.CompositePredicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.{Name, ~}
import uk.gov.hmrc.domain
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.frontend.binders.SafeRedirectUrl

import scala.concurrent.{ExecutionContext, Future}

class AuthActionImpl @Inject()(
  val authConnector: PertaxAuthConnector,
  configuration: Configuration,
  configDecorator: ConfigDecorator)(implicit ec: ExecutionContext)
    extends AuthAction with AuthorisedFunctions {

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {

    val compositePredicate =
      CompositePredicate(ConfidenceLevel.L200, CredentialStrength(CredentialStrength.strong))

    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
    authorised(compositePredicate)
      .retrieve(
        Retrievals.nino and
          Retrievals.allEnrolments and
          Retrievals.credentials and
          Retrievals.confidenceLevel and
          Retrievals.name and
          Retrievals.loginTimes and
          Retrievals.trustedHelper and
          Retrievals.profile) {

        case nino ~ Enrolments(enrolments) ~ Some(credentials) ~ confidenceLevel ~ name ~ logins ~ Some(trustedHelper) ~ profile =>
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
              Some(domain.Nino(trustedHelper.principalNino)),
              None,
              credentials,
              confidenceLevel,
              Some(UserName(Name(Some(trustedHelper.principalName), None))),
              logins.previousLogin,
              Some(trustedHelper),
              profile,
              trimmedRequest
            )
          )

        case nino ~ Enrolments(enrolments) ~ Some(credentials) ~ confidenceLevel ~ name ~ logins ~ None ~ profile =>
          val saEnrolment = enrolments.find(_.key == "IR-SA").flatMap { enrolment =>
            enrolment.identifiers
              .find(id => id.key == "UTR")
              .map(key => SelfAssessmentEnrolment(SaUtr(key.value), SelfAssessmentStatus.fromString(enrolment.state)))
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
              credentials,
              confidenceLevel,
              Some(UserName(name.getOrElse(Name(None, None)))),
              logins.previousLogin,
              None,
              profile,
              trimmedRequest
            )
          )

        case _ => throw new RuntimeException("Can't find credentials for user")
      }
  } recover {
    case _: NoActiveSession => {

      def postSignInRedirectUrl(implicit request: Request[_]) =
        configDecorator.pertaxFrontendHost + controllers.routes.ApplicationController
          .uplift(Some(SafeRedirectUrl(configDecorator.pertaxFrontendHost + request.path)))
          .url

      request.session.get(SessionKeys.authProvider) match {
        case Some(configDecorator.authProviderVerify) => {
          lazy val idaSignIn = s"${configDecorator.citizenAuthHost}/${configDecorator.ida_web_context}/login"
          Redirect(idaSignIn).withSession(
            "loginOrigin"    -> configDecorator.defaultOrigin.origin,
            "login_redirect" -> postSignInRedirectUrl(request)
          )
        }
        case Some(configDecorator.authProviderGG) => {
          lazy val ggSignIn = s"${configDecorator.companyAuthHost}/${configDecorator.gg_web_context}"
          Redirect(
            ggSignIn,
            Map(
              "continue"    -> Seq(postSignInRedirectUrl(request)),
              "accountType" -> Seq("individual"),
              "origin"      -> Seq(configDecorator.defaultOrigin.origin)
            )
          )
        }
        case _ => Redirect(configDecorator.authProviderChoice)
      }
    }

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

    case _: IncorrectCredentialStrength =>
      Redirect(
        configDecorator.multiFactorAuthenticationUpliftUrl,
        Map(
          "origin"      -> Seq(configDecorator.origin),
          "continueUrl" -> Seq(configDecorator.pertaxFrontendHost + configDecorator.personalAccount)
        )
      )

    case _: InsufficientEnrolments => throw InsufficientEnrolments("")
  }
}

@ImplementedBy(classOf[AuthActionImpl])
trait AuthAction extends ActionBuilder[AuthenticatedRequest] with ActionFunction[Request, AuthenticatedRequest]
