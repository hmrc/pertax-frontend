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

package controllers

import com.google.inject.Inject
import config.ConfigDecorator
import controllers.auth._
import play.api.Logger
import play.api.mvc._
import services._
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl.idFunctor
import uk.gov.hmrc.play.bootstrap.binders.{OnlyRelative, RedirectUrl, SafeRedirectUrl}
import uk.gov.hmrc.time.CurrentTaxYear
import views.html.iv.failure._
import views.html.iv.success.SuccessView

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class ApplicationController @Inject() (
  val identityVerificationFrontendService: IdentityVerificationFrontendService,
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  successView: SuccessView,
  cannotConfirmIdentityView: CannotConfirmIdentityView,
  failedIvIncompleteView: FailedIvIncompleteView,
  lockedOutView: LockedOutView,
  timeOutView: TimeOutView,
  technicalIssuesView: TechnicalIssuesView
)(implicit configDecorator: ConfigDecorator, val ec: ExecutionContext)
    extends PertaxBaseController(cc)
    with CurrentTaxYear {

  private val logger = Logger(this.getClass)

  override def now: () => LocalDate = () => LocalDate.now()

  def uplift(redirectUrl: Option[SafeRedirectUrl]): Action[AnyContent] = Action.async {
    Future.successful(Redirect(redirectUrl.map(_.url).getOrElse(routes.HomeController.index.url)))
  }

  def showUpliftJourneyOutcome(continueUrl: Option[SafeRedirectUrl]): Action[AnyContent] =
    Action.async { implicit request =>
      val journeyId =
        List(request.getQueryString("token"), request.getQueryString("journeyId")).flatten.headOption

      val retryUrl = routes.ApplicationController.uplift(continueUrl).url

      journeyId match {
        case Some(jid) =>
          identityVerificationFrontendService
            .getIVJourneyStatus(jid)
            .map {
              case Success =>
                Ok(successView(continueUrl.map(_.url).getOrElse(routes.HomeController.index.url)))

              case InsufficientEvidence =>
                Redirect(routes.SelfAssessmentController.ivExemptLandingPage(continueUrl))

              case UserAborted =>
                logErrorMessage(UserAborted.toString)
                Unauthorized(cannotConfirmIdentityView(retryUrl))

              case FailedMatching =>
                logErrorMessage(FailedMatching.toString)
                Unauthorized(cannotConfirmIdentityView(retryUrl))

              case Incomplete =>
                logErrorMessage(Incomplete.toString)
                Unauthorized(failedIvIncompleteView(retryUrl))

              case PrecondFailed =>
                logErrorMessage(PrecondFailed.toString)
                Unauthorized(cannotConfirmIdentityView(retryUrl))

              case LockedOut =>
                logErrorMessage(LockedOut.toString)
                Unauthorized(lockedOutView(allowContinue = false))

              case Timeout =>
                logErrorMessage(Timeout.toString)
                InternalServerError(timeOutView(retryUrl))

              case TechnicalIssue =>
                logger.warn(s"TechnicalIssue response from identityVerificationFrontendService")
                InternalServerError(technicalIssuesView(retryUrl))

              case _ =>
                InternalServerError(technicalIssuesView(retryUrl))
            }
            .getOrElse(BadRequest(technicalIssuesView(retryUrl)))
      }
    }

  private def logErrorMessage(reason: String): Unit =
    logger.warn(s"Unable to confirm user identity: $reason")

  def signout(continueUrl: Option[RedirectUrl], origin: Option[Origin]): Action[AnyContent] =
    authJourney.minimumAuthWithSelfAssessment { implicit request =>
      val safeUrl = continueUrl.flatMap { redirectUrl =>
        redirectUrl.getEither(OnlyRelative) match {
          case Right(safeRedirectUrl) => Some(safeRedirectUrl.url)
          case _                      => Some(configDecorator.getFeedbackSurveyUrl(configDecorator.defaultOrigin))
        }
      }
      safeUrl
        .orElse(origin.map(configDecorator.getFeedbackSurveyUrl))
        .fold(BadRequest("Missing origin")) { url: String =>
          Redirect(configDecorator.getBasGatewayFrontendSignOutUrl(url))
        }
    }
}
