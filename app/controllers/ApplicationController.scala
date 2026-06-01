/*
 * Copyright 2023 HM Revenue & Customs
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

package controllers

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.bindable.Origin
import play.api.Logger
import play.api.mvc.*
import services.*
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl.idFunctor
import uk.gov.hmrc.play.bootstrap.binders.{OnlyRelative, RedirectUrl, SafeRedirectUrl}
import uk.gov.hmrc.time.CurrentTaxYear
import views.html.iv.failure.*
import views.html.iv.success.SuccessView
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, AuthorisedFunctions, NoActiveSession}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class ApplicationController @Inject() (
  val identityVerificationFrontendService: IdentityVerificationFrontendService,
  val authConnector: AuthConnector,
  cc: MessagesControllerComponents,
  successView: SuccessView,
  technicalIssuesView: TechnicalIssuesView
)(implicit configDecorator: ConfigDecorator, val ec: ExecutionContext)
    extends PertaxBaseController(cc)
    with CurrentTaxYear
    with AuthorisedFunctions {

  private val logger = Logger(this.getClass)

  override def now: () => LocalDate = () => LocalDate.now()

  def uplift(redirectUrl: Option[RedirectUrl]): Action[AnyContent] = Action.async {

    redirectUrl.fold(Future.successful(Redirect(routes.HomeController.index.url))) {
      _.getEither(OnlyRelative) match {
        case Right(safeRedirectUrl) => Future.successful(Redirect(safeRedirectUrl.url))
        case Left(error)            => Future.successful(BadRequest(error))
      }
    }
  }

  def showUpliftJourneyOutcome(continueUrl: Option[RedirectUrl]): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val hc: HeaderCarrier =
        HeaderCarrierConverter.fromRequestAndSession(request, request.session)

      authorised() {
        val safeUrl: Either[String, Option[SafeRedirectUrl]] =
          continueUrl.map(_.getEither(OnlyRelative).map(Some.apply)).getOrElse(Right(None))

        processSafeUrl(safeUrl, continueUrl)
      } recover {
        case _: NoActiveSession        =>
          logger.warn("NoActiveSession encountered during authorisation")
          Redirect(routes.HomeController.index.url)
        case e: AuthorisationException =>
          logger.warn(s"AuthorisationException encountered: ${e.getMessage}")
          Redirect(routes.HomeController.index.url)
      }
    }

  private def processSafeUrl(safeUrl: Either[String, Option[SafeRedirectUrl]], continueUrl: Option[RedirectUrl])(
    implicit request: Request[AnyContent]
  ): Future[Result] =
    safeUrl match {
      case Left(error)    => Future.successful(BadRequest(error))
      case Right(safeUrl) =>
        val retryUrl  = routes.ApplicationController.uplift(continueUrl).url
        val journeyId = List(request.getQueryString("token"), request.getQueryString("journeyId")).flatten.headOption

        processJourneyId(
          journeyId,
          retryUrl,
          continueUrl,
          safeUrl.map(_.url).getOrElse(routes.HomeController.index.url)
        )
    }
  private def processJourneyId(
    journeyId: Option[String],
    retryUrl: String,
    continueUrl: Option[RedirectUrl],
    safeUrl: String
  )(implicit request: Request[AnyContent]): Future[Result] =
    journeyId match {
      case Some(jid) =>
        identityVerificationFrontendService
          .getIVJourneyStatus(jid)
          .map {
            case Success => Ok(successView(safeUrl))

            case InsufficientEvidence => Redirect(routes.SelfAssessmentController.ivExemptLandingPage(continueUrl))

            case PrecondFailed =>
              logger.error(
                s"PreconditionFailed response from IdentityVerificationFrontendService for journeyId: $jid. " +
                  "This outcome should not occur because the user should not have been able to enter the service. " +
                  "Investigate the identity verification journey for this journeyId."
              )
              InternalServerError(technicalIssuesView(retryUrl))

            case TechnicalIssue =>
              logger.warn(s"TechnicalIssue response from IdentityVerificationFrontendService for journeyId: $jid")
              InternalServerError(technicalIssuesView(retryUrl))

            case _ => InternalServerError(technicalIssuesView(retryUrl))
          }
          .getOrElse(BadRequest(technicalIssuesView(retryUrl)))
      case _         =>
        logger.error("journeyId missing or incorrect")
        Future.successful(InternalServerError(technicalIssuesView(retryUrl)))
    }

  def signout(continueUrl: Option[RedirectUrl], origin: Option[Origin]): Action[AnyContent] =
    Action {
      val safeUrl = continueUrl.flatMap { redirectUrl =>
        redirectUrl.getEither(OnlyRelative) match {
          case Right(safeRedirectUrl) =>
            Some(safeRedirectUrl.url)
          case _                      =>
            Some(configDecorator.getFeedbackSurveyUrl(configDecorator.defaultOrigin))
        }
      }
      safeUrl
        .orElse(origin.map(configDecorator.getFeedbackSurveyUrl))
        .fold(BadRequest("Missing origin")) { (url: String) =>
          Redirect(configDecorator.getBasGatewayFrontendSignOutUrl(url))
        }
    }
}
