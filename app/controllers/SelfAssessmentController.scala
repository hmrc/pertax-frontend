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

import com.google.inject.Inject
import config.ConfigDecorator
import connectors.PertaxAuditConnector
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import error.RendersErrors
import models._
import org.joda.time.DateTime
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.frontend.binders.SafeRedirectUrl
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.CurrentTaxYear
import util.AuditServiceTools.buildEvent
import util.{DateTimeTools, LocalPartialRetriever}

import scala.concurrent.Future

class SelfAssessmentController @Inject()(
  val messagesApi: MessagesApi,
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  auditConnector: PertaxAuditConnector)(
  implicit partialRetriever: LocalPartialRetriever,
  configDecorator: ConfigDecorator,
  val templateRenderer: TemplateRenderer)
    extends PertaxBaseController with CurrentTaxYear with RendersErrors {

  override def now: () => DateTime = () => DateTime.now()

  def handleSelfAssessment: Action[AnyContent] =
    (authJourney.authWithPersonalDetails andThen withBreadcrumbAction.addBreadcrumb(baseBreadcrumb)) {
      implicit request =>
        if (request.isGovernmentGateway) {
          request.saUserType match {
            case NotYetActivatedOnlineFilerSelfAssessmentUser(_) =>
              Redirect(configDecorator.ssoToActivateSaEnrolmentPinUrl)
            case WrongCredentialsSelfAssessmentUser(_) =>
              Redirect(controllers.routes.SaWrongCredentialsController.landingPage())
            case NotEnrolledSelfAssessmentUser(saUtr) =>
              Redirect(controllers.routes.SelfAssessmentController.requestAccess())
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
        case WrongCredentialsSelfAssessmentUser(_) =>
          handleIvExemptAuditing("Wrong credentials SA filer")
          Redirect(controllers.routes.SaWrongCredentialsController.landingPage())
        case NotEnrolledSelfAssessmentUser(saUtr) =>
          handleIvExemptAuditing("Never enrolled SA filer")
          Redirect(controllers.routes.SelfAssessmentController.requestAccess())
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

  def requestAccess: Action[AnyContent] =
    authJourney.minimumAuthWithSelfAssessment { implicit request =>
      request.saUserType match {
        case NotEnrolledSelfAssessmentUser(saUtr) =>
          val deadlineYear = current.finishYear.toString
          Ok(views.html.selfassessment.requestAccessToSelfAssessment(saUtr.utr, deadlineYear))
        case _ => Redirect(routes.HomeController.index())
      }
    }

}
