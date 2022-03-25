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
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import controllers.controllershelpers.PaperlessInterruptHelper
import error.ErrorRenderer
import models._
import play.api.Logging
import play.api.mvc._
import play.twirl.api.Html
import services.PreferencesFrontendService
import services.partials.{FormPartialService, SaPartialService}
import uk.gov.hmrc.play.partials.HtmlPartial
import uk.gov.hmrc.renderer.TemplateRenderer
import util.DateTimeTools.previousAndCurrentTaxYearFromGivenYear
import views.html.SelfAssessmentSummaryView
import views.html.interstitial.{ViewChildBenefitsSummaryInterstitialView, ViewNationalInsuranceInterstitialHomeView, ViewNewsAndUpdatesView}
import views.html.selfassessment.Sa302InterruptView

import scala.concurrent.{ExecutionContext, Future}

class InterstitialController @Inject() (
  val formPartialService: FormPartialService,
  val saPartialService: SaPartialService,
  val preferencesFrontendService: PreferencesFrontendService,
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  cc: MessagesControllerComponents,
  errorRenderer: ErrorRenderer,
  viewNationalInsuranceInterstitialHomeView: ViewNationalInsuranceInterstitialHomeView,
  viewChildBenefitsSummaryInterstitialView: ViewChildBenefitsSummaryInterstitialView,
  selfAssessmentSummaryView: SelfAssessmentSummaryView,
  sa302InterruptView: Sa302InterruptView,
  viewNewsAndUpdatesView: ViewNewsAndUpdatesView
)(implicit configDecorator: ConfigDecorator, val templateRenderer: TemplateRenderer, ec: ExecutionContext)
    extends PertaxBaseController(cc) with PaperlessInterruptHelper with Logging {

  val saBreadcrumb: Breadcrumb =
    "label.self_assessment" -> routes.InterstitialController.displaySelfAssessment().url ::
      baseBreadcrumb

  private def currentUrl(implicit request: Request[AnyContent]) =
    configDecorator.pertaxFrontendHost + request.path

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails andThen withBreadcrumbAction
      .addBreadcrumb(baseBreadcrumb)
  private val authenticateSa: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails andThen withBreadcrumbAction
      .addBreadcrumb(saBreadcrumb)

  def displayNationalInsurance: Action[AnyContent] = authenticate.async { implicit request =>
    formPartialService.getNationalInsurancePartial.flatMap { p =>
      Future.successful(
        Ok(
          viewNationalInsuranceInterstitialHomeView(
            formPartial = p successfulContentOrElse Html(""),
            redirectUrl = currentUrl,
            request.nino
          )
        )
      )
    }
  }

  def displayChildBenefits: Action[AnyContent] = authenticate { implicit request =>
    Ok(
      viewChildBenefitsSummaryInterstitialView(
        redirectUrl = currentUrl,
        taxCreditsEnabled = configDecorator.taxCreditsEnabled
      )
    )
  }

  def displaySelfAssessment: Action[AnyContent] = authenticate.async { implicit request =>
    if (request.isSaUserLoggedIntoCorrectAccount && request.isGovernmentGateway) {
      val formPartial = formPartialService.getSelfAssessmentPartial recoverWith { case _ =>
        Future.successful(HtmlPartial.Failure(None, ""))
      }
      val saPartial = saPartialService.getSaAccountSummary recoverWith { case _ =>
        Future.successful(HtmlPartial.Failure(None, ""))
      }

      for {
        formPartial <- formPartial
        saPartial   <- saPartial
      } yield Ok(
        selfAssessmentSummaryView(
          formPartial successfulContentOrElse Html(""),
          saPartial successfulContentOrElse Html("")
        )
      )
    } else errorRenderer.futureError(UNAUTHORIZED)

  }

  def displaySa302Interrupt(year: Int): Action[AnyContent] = authenticateSa { implicit request =>
    request.saUserType match {
      case ActivatedOnlineFilerSelfAssessmentUser(saUtr) =>
        Ok(sa302InterruptView(year = previousAndCurrentTaxYearFromGivenYear(year), saUtr = saUtr))
      case _ =>
        logger.warn("User had no sa account when one was required")
        errorRenderer.error(UNAUTHORIZED)
    }
  }

  def displayNewsAndUpdates: Action[AnyContent] = authenticate { implicit request =>
      Ok(
        viewNewsAndUpdatesView(
          redirectUrl = currentUrl
        )
      )
  }
}
