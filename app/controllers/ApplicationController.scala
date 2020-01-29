/*
 * Copyright 2020 HM Revenue & Customs
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
import error.RendersErrors
import io.lemonlabs.uri.{QueryString, Url}
import org.joda.time.DateTime
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc._
import services.IdentityVerificationSuccessResponse._
import services._
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.play.frontend.binders.SafeRedirectUrl
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.CurrentTaxYear
import util.LocalPartialRetriever

import scala.concurrent.Future

class ApplicationController @Inject()(
  val messagesApi: MessagesApi,
  val identityVerificationFrontendService: IdentityVerificationFrontendService,
  authJourney: AuthJourney)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  val templateRenderer: TemplateRenderer)
    extends PertaxBaseController with CurrentTaxYear with RendersErrors {

  override def now: () => DateTime = () => DateTime.now()

  def uplift(redirectUrl: Option[SafeRedirectUrl]): Action[AnyContent] = Action.async {
    Future.successful(Redirect(redirectUrl.map(_.url).getOrElse(routes.HomeController.index().url)))
  }

  def showUpliftJourneyOutcome(continueUrl: Option[SafeRedirectUrl]): Action[AnyContent] =
    Action.async { implicit request =>
      val journeyId =
        List(request.getQueryString("token"), request.getQueryString("journeyId")).flatten.headOption

      val retryUrl = controllers.routes.ApplicationController.uplift(continueUrl).url

      journeyId match {
        case Some(jid) =>
          identityVerificationFrontendService.getIVJourneyStatus(jid).map {
            case IdentityVerificationSuccessResponse(Success) =>
              Ok(views.html.iv.success.success(continueUrl.map(_.url).getOrElse(routes.HomeController.index().url)))
            case IdentityVerificationSuccessResponse(InsufficientEvidence) =>
              Redirect(controllers.routes.SelfAssessmentController.ivExemptLandingPage(continueUrl))
            case IdentityVerificationSuccessResponse(UserAborted) =>
              Logger.warn(s"Unable to confirm user identity: $UserAborted")
              Unauthorized(views.html.iv.failure.cantConfirmIdentity(retryUrl))
            case IdentityVerificationSuccessResponse(FailedMatching) =>
              Logger.warn(s"Unable to confirm user identity: $FailedMatching")
              Unauthorized(views.html.iv.failure.cantConfirmIdentity(retryUrl))
            case IdentityVerificationSuccessResponse(Incomplete) =>
              Logger.warn(s"Unable to confirm user identity: $Incomplete")
              Unauthorized(views.html.iv.failure.failedIvIncomplete(retryUrl))
            case IdentityVerificationSuccessResponse(PrecondFailed) =>
              Logger.warn(s"Unable to confirm user identity: $PrecondFailed")
              Unauthorized(views.html.iv.failure.cantConfirmIdentity(retryUrl))
            case IdentityVerificationSuccessResponse(LockedOut) =>
              Logger.warn(s"Unable to confirm user identity: $LockedOut")
              Unauthorized(views.html.iv.failure.lockedOut(allowContinue = false))
            case IdentityVerificationSuccessResponse(Timeout) =>
              Logger.warn(s"Unable to confirm user identity: $Timeout")
              InternalServerError(views.html.iv.failure.timeOut(retryUrl))
            case IdentityVerificationSuccessResponse(TechnicalIssue) =>
              Logger.warn(s"TechnicalIssue response from identityVerificationFrontendService")
              InternalServerError(views.html.iv.failure.technicalIssues(retryUrl))
            case r =>
              Logger.error(s"Unhandled response from identityVerificationFrontendService: $r")
              InternalServerError(views.html.iv.failure.technicalIssues(retryUrl))
          }
        case None =>
          Logger.error(s"No journeyId present when displaying IV uplift journey outcome")
          Future.successful(BadRequest(views.html.iv.failure.technicalIssues(retryUrl)))
      }
    }

  def signout(continueUrl: Option[SafeRedirectUrl], origin: Option[Origin]): Action[AnyContent] =
    authJourney.authWithSelfAssessment { implicit request =>
      continueUrl
        .map(_.url)
        .orElse(origin.map(configDecorator.getFeedbackSurveyUrl))
        .fold(BadRequest("Missing origin")) { url: String =>
          if (request.isGovernmentGateway) {
            Redirect(configDecorator.getCompanyAuthFrontendSignOutUrl(url))
          } else {
            Redirect(configDecorator.citizenAuthFrontendSignOut).withSession("postLogoutPage" -> url)
          }
        }
    }

  def handleFailedAuthentication: Action[AnyContent] =
    Action.async { implicit request =>
      Logger.error(s"Organisation/Agent has incorrect enrolments")
      unauthorizedFutureError(ggSignInUrl)
    }

  def ggSignInUrl: String = {
    lazy val ggSignIn = s"${configDecorator.companyAuthHost}/${configDecorator.gg_web_context}"

    val continueUrl = configDecorator.pertaxFrontendHost + configDecorator.personalAccount

    Url(
      path = ggSignIn,
      query = QueryString.fromPairs(
        "continue"    -> continueUrl,
        "accountType" -> "individual",
        "origin"      -> configDecorator.defaultOrigin.origin)
    ).toString()
  }
}
