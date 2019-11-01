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

package controllers

import config.ConfigDecorator
import connectors.PertaxAuditConnector
import controllers.auth._
import controllers.auth.requests.UserRequest
import error.RendersErrors
import com.google.inject.Inject
import models._
import org.joda.time.DateTime
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc._
import services.IdentityVerificationSuccessResponse._
import services._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.binders.Origin
import uk.gov.hmrc.play.frontend.binders.SafeRedirectUrl
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.CurrentTaxYear
import util.AuditServiceTools._
import util.{DateTimeTools, LocalPartialRetriever}

import scala.concurrent.Future

class ApplicationController @Inject()(
  val messagesApi: MessagesApi,
  val identityVerificationFrontendService: IdentityVerificationFrontendService,
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  auditConnector: PertaxAuditConnector)(
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
              Redirect(controllers.routes.ApplicationController.ivExemptLandingPage(continueUrl))
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

  def handleSelfAssessment: Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)) {
      implicit request =>
        if (request.isGovernmentGateway) {
          request.saUserType match {
            case NotYetActivatedOnlineFilerSelfAssessmentUser(_) =>
              Redirect(configDecorator.ssoToActivateSaEnrolmentPinUrl)
            case wrongCredentialsUser: WrongCredentialsSelfAssessmentUser =>
              Redirect(controllers.routes.SaWrongCredentialsController.landingPage())
            case _ => Redirect(routes.HomeController.index())
          }
        } else {
          error(INTERNAL_SERVER_ERROR)
        }
    }

  def ivExemptLandingPage(continueUrl: Option[SafeRedirectUrl]): Action[AnyContent] =
    authJourney.minimumAuthWithSelfAssessment { implicit request =>
      val retryUrl = controllers.routes.ApplicationController.uplift(continueUrl).url

      request.saUserType match {
        case ActivatedOnlineFilerSelfAssessmentUser(x) =>
          handleIvExemptAuditing("Activated online SA filer")
          Ok(views.html.activatedSaFilerIntermediate(x.toString, DateTimeTools.previousAndCurrentTaxYear))
        case NotYetActivatedOnlineFilerSelfAssessmentUser(_) =>
          handleIvExemptAuditing("Not yet activated SA filer")
          Ok(views.html.iv.failure.failedIvContinueToActivateSa())
        case wrongCredentialsUser: WrongCredentialsSelfAssessmentUser =>
          handleIvExemptAuditing("Ambiguous SA filer")
          Ok(views.html.selfAssessmentNotShown(wrongCredentialsUser.saUtr))
        case NonFilerSelfAssessmentUser =>
          Ok(views.html.iv.failure.cantConfirmIdentity(retryUrl))
      }
    }

  private def handleIvExemptAuditing(
    saUserType: String)(implicit hc: HeaderCarrier, request: UserRequest[_]): Future[AuditResult] =
    auditConnector.sendEvent(
      buildEvent(
        "saIdentityVerificationBypass",
        "sa17_exceptions_or_insufficient_evidence",
        Map("saUserType" -> Some(saUserType))))

}
