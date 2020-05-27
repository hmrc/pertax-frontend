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
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import error.RendersErrors
import models._
import org.joda.time.DateTime
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.bootstrap.binders.SafeRedirectUrl
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.CurrentTaxYear
import util.AuditServiceTools.buildEvent
import util.{DateTimeTools, LocalPartialRetriever}
import views.html.ActivatedSaFilerIntermediateView
import views.html.iv.failure.{CannotConfirmIdentityView, FailedIvContinueToActivateSaView}
import views.html.selfassessment.RequestAccessToSelfAssessmentView

import scala.concurrent.{ExecutionContext, Future}

class SelfAssessmentController @Inject()(
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  auditConnector: AuditConnector,
  cc: MessagesControllerComponents,
  activatedSaFilerIntermediateView: ActivatedSaFilerIntermediateView,
  failedIvContinueToActivateSaView: FailedIvContinueToActivateSaView,
  cannotConfirmIdentityView: CannotConfirmIdentityView,
  requestAccessToSelfAssessmentView: RequestAccessToSelfAssessmentView)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  val templateRenderer: TemplateRenderer,
  ec: ExecutionContext)
    extends PertaxBaseController(cc) with CurrentTaxYear with RendersErrors {

  override def now: () => DateTime = () => DateTime.now()

  def handleSelfAssessment: Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)) {
      implicit request =>
        if (request.isGovernmentGateway) {
          request.saUserType match {
            case NotYetActivatedOnlineFilerSelfAssessmentUser(_) =>
              Redirect(configDecorator.ssoToActivateSaEnrolmentPinUrl)
            case WrongCredentialsSelfAssessmentUser(_) =>
              Redirect(routes.SaWrongCredentialsController.landingPage())
            case NotEnrolledSelfAssessmentUser(_) =>
              Redirect(routes.SelfAssessmentController.requestAccess())
            case _ => Redirect(routes.HomeController.index())
          }
        } else {
          error(INTERNAL_SERVER_ERROR)
        }
    }

  def ivExemptLandingPage(continueUrl: Option[SafeRedirectUrl]): Action[AnyContent] =
    authJourney.minimumAuthWithSelfAssessment { implicit request =>
      val retryUrl = routes.ApplicationController.uplift(continueUrl).url

      request.saUserType match {
        case ActivatedOnlineFilerSelfAssessmentUser(x) =>
          handleIvExemptAuditing("Activated online SA filer")
          Ok(activatedSaFilerIntermediateView(x.toString, DateTimeTools.previousAndCurrentTaxYear))
        case NotYetActivatedOnlineFilerSelfAssessmentUser(_) =>
          handleIvExemptAuditing("Not yet activated SA filer")
          Ok(failedIvContinueToActivateSaView())
        case WrongCredentialsSelfAssessmentUser(_) =>
          handleIvExemptAuditing("Wrong credentials SA filer")
          Redirect(routes.SaWrongCredentialsController.landingPage())
        case NotEnrolledSelfAssessmentUser(_) =>
          handleIvExemptAuditing("Never enrolled SA filer")
          Redirect(routes.SelfAssessmentController.requestAccess())
        case NonFilerSelfAssessmentUser =>
          Ok(cannotConfirmIdentityView(retryUrl))
      }
    }

  private def handleIvExemptAuditing(
    saUserType: String)(implicit hc: HeaderCarrier, request: UserRequest[_]): Future[AuditResult] =
    auditConnector.sendEvent(
      buildEvent(
        "saIdentityVerificationBypass",
        "sa17_exceptions_or_insufficient_evidence",
        Map("saUserType" -> Some(saUserType))))

  def requestAccess: Action[AnyContent] =
    authJourney.minimumAuthWithSelfAssessment { implicit request =>
      request.saUserType match {
        case NotEnrolledSelfAssessmentUser(saUtr) =>
          val deadlineYear = current.finishYear.toString
          Ok(requestAccessToSelfAssessmentView(saUtr.utr, deadlineYear))
        case _ => Redirect(routes.HomeController.index())
      }
    }
}
