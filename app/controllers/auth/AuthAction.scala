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

import com.google.inject.{ImplementedBy, Inject}
import config.ConfigDecorator
import controllers.auth.requests.AuthenticatedRequest
import controllers.routes
import io.lemonlabs.uri.Url
import models.UserName
import play.api.mvc.Results.Redirect
import play.api.mvc._
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Individual, Organisation}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.{Name, ~}
import uk.gov.hmrc.domain
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.binders.SafeRedirectUrl

import scala.concurrent.{ExecutionContext, Future}

class AuthActionImpl @Inject() (
  val authConnector: AuthConnector,
  configDecorator: ConfigDecorator,
  sessionAuditor: SessionAuditor,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends AuthAction with AuthorisedFunctions {

  def addRedirect(profileUrl: Option[String]): Option[String] =
    for {
      url <- profileUrl
      res <- Url.parseOption(url).filter(parsed => parsed.schemeOption.isDefined)
    } yield res.replaceParams("redirect_uri", configDecorator.pertaxFrontendBackLink).toString()

  object LT200 {
    def unapply(confLevel: ConfidenceLevel): Option[ConfidenceLevel] =
      if (confLevel.level < ConfidenceLevel.L200.level) Some(confLevel) else None
  }

  object GTOE200 {
    def unapply(confLevel: ConfidenceLevel): Option[ConfidenceLevel] =
      if (confLevel.level >= ConfidenceLevel.L200.level) Some(confLevel) else None
  }

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {

    val compositePredicate =
      CredentialStrength(CredentialStrength.weak) or
        CredentialStrength(CredentialStrength.strong)

    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    authorised(compositePredicate)
      .retrieve(
        Retrievals.nino and
          Retrievals.affinityGroup and
          Retrievals.allEnrolments and
          Retrievals.credentials and
          Retrievals.credentialStrength and
          Retrievals.confidenceLevel and
          Retrievals.name and
          Retrievals.trustedHelper and
          Retrievals.profile
      ) {

        case _ ~ Some(Individual) ~ _ ~ _ ~ (Some(CredentialStrength.weak) | None) ~ _ ~ _ ~ _ ~ _ =>
          upliftCredentialStrength

        case _ ~ Some(Individual) ~ _ ~ _ ~ _ ~ LT200(_) ~ _ ~ _ ~ _ =>
          upliftConfidenceLevel(request)

        case _ ~ Some(Organisation | Agent) ~ _ ~ _ ~ _ ~ LT200(_) ~ _ ~ _ ~ _ =>
          upliftConfidenceLevel(request)

        case _ ~ Some(Organisation | Agent) ~ _ ~ _ ~ (Some(CredentialStrength.weak) | None) ~ _ ~ _ ~ _ ~ _ =>
          upliftCredentialStrength

        case nino ~ _ ~ Enrolments(enrolments) ~ Some(credentials) ~ Some(CredentialStrength.strong) ~ GTOE200(
              confidenceLevel
            ) ~ name ~ trustedHelper ~ profile =>
          val trimmedRequest: Request[A] = request
            .map {
              case AnyContentAsFormUrlEncoded(data) =>
                AnyContentAsFormUrlEncoded(data.map { case (key, vals) =>
                  (key, vals.map(_.trim))
                })
              case b => b
            }
            .asInstanceOf[Request[A]]

          def singleAccountEnrolmentPresent(enrolments: Set[Enrolment]) =
            enrolments
              .find(_.key == "HMRC-PT")
              .flatMap { enrolment =>
                enrolment.identifiers
                  .find(id => id.key == "NINO")
              }
              .nonEmpty

          val authenticatedRequest = AuthenticatedRequest[A](
            trustedHelper.fold(nino.map(domain.Nino))(helper => Some(domain.Nino(helper.principalNino))),
            credentials,
            confidenceLevel,
            Some(
              UserName(
                trustedHelper.fold(name.getOrElse(Name(None, None)))(helper => Name(Some(helper.principalName), None))
              )
            ),
            trustedHelper,
            addRedirect(profile),
            enrolments,
            trimmedRequest
          )

          for {
            result        <- block(authenticatedRequest)
            updatedResult <- sessionAuditor.auditOnce(authenticatedRequest, result)
          } yield
            if (singleAccountEnrolmentPresent(enrolments)) updatedResult
            else Redirect(SafeRedirectUrl(configDecorator.taxEnrolmentDeniedRedirect(Some(request.uri))).url)

        case _ => throw new RuntimeException("Can't find credentials for user")
      }
  } recover {
    case _: NoActiveSession =>
      def postSignInRedirectUrl(implicit request: Request[_]) =
        configDecorator.pertaxFrontendForAuthHost + controllers.routes.ApplicationController
          .uplift(Some(SafeRedirectUrl(configDecorator.pertaxFrontendForAuthHost + request.path)))
          .url

      request.session.get(configDecorator.authProviderKey) match {
        case Some(configDecorator.authProviderVerify) =>
          lazy val idaSignIn = s"${configDecorator.citizenAuthHost}/ida/login"
          Redirect(idaSignIn).withSession(
            "loginOrigin"    -> configDecorator.defaultOrigin.origin,
            "login_redirect" -> postSignInRedirectUrl(request)
          )
        case Some(configDecorator.authProviderGG) =>
          lazy val ggSignIn = s"${configDecorator.basGatewayFrontendHost}/bas-gateway/sign-in"
          Redirect(
            ggSignIn,
            Map(
              "continue_url" -> Seq(postSignInRedirectUrl(request)),
              "accountType"  -> Seq("individual"),
              "origin"       -> Seq(configDecorator.defaultOrigin.origin)
            )
          )
        case _ => Redirect(configDecorator.authProviderChoice)
      }

    case _: IncorrectCredentialStrength => Redirect(configDecorator.authProviderChoice)

    case _: InsufficientEnrolments => throw InsufficientEnrolments("")
  }

  private def upliftCredentialStrength: Future[Result] =
    Future.successful(
      Redirect(
        configDecorator.multiFactorAuthenticationUpliftUrl,
        Map(
          "origin"      -> Seq(configDecorator.origin),
          "continueUrl" -> Seq(configDecorator.pertaxFrontendForAuthHost + configDecorator.personalAccount)
        )
      )
    )

  private def upliftConfidenceLevel(request: Request[_]): Future[Result] =
    Future.successful(
      Redirect(
        configDecorator.identityVerificationUpliftUrl,
        Map(
          "origin"          -> Seq(configDecorator.origin),
          "confidenceLevel" -> Seq(ConfidenceLevel.L200.toString),
          "completionURL" -> Seq(
            configDecorator.pertaxFrontendForAuthHost + routes.ApplicationController
              .showUpliftJourneyOutcome(Some(SafeRedirectUrl(request.uri)))
          ),
          "failureURL" -> Seq(
            configDecorator.pertaxFrontendForAuthHost + routes.ApplicationController
              .showUpliftJourneyOutcome(Some(SafeRedirectUrl(request.uri)))
          )
        )
      )
    )

  override def parser: BodyParser[AnyContent] = cc.parsers.defaultBodyParser

  override protected def executionContext: ExecutionContext = cc.executionContext
}

@ImplementedBy(classOf[AuthActionImpl])
trait AuthAction
    extends ActionBuilder[AuthenticatedRequest, AnyContent] with ActionFunction[Request, AuthenticatedRequest]
