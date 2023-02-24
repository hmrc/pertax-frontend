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
import config.{ConfigDecorator, NewsAndTilesConfig}
import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import controllers.controllershelpers.PaperlessInterruptHelper
import error.ErrorRenderer
import models._
import models.admin.{ChildBenefitSingleAccountToggle, ItsaMessageToggle}
import play.api.Logging
import play.api.mvc._
import play.twirl.api.Html
import services.SeissService
import services.admin.FeatureFlagService
import services.partials.{FormPartialService, SaPartialService}
import uk.gov.hmrc.play.partials.HtmlPartial
import util.DateTimeTools._
import util.{EnrolmentsHelper, FormPartialUpgrade}
import views.html.SelfAssessmentSummaryView
import views.html.interstitial._
import views.html.selfassessment.Sa302InterruptView

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class InterstitialController @Inject() (
  val formPartialService: FormPartialService,
  val saPartialService: SaPartialService,
  val preferencesFrontendService: PreferencesFrontendConnector,
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  cc: MessagesControllerComponents,
  errorRenderer: ErrorRenderer,
  viewNationalInsuranceInterstitialHomeView: ViewNationalInsuranceInterstitialHomeView,
  viewChildBenefitsSummaryInterstitialView: ViewChildBenefitsSummaryInterstitialView,
  viewChildBenefitsSummarySingleAccountInterstitialView: ViewChildBenefitsSummarySingleAccountInterstitialView,
  selfAssessmentSummaryView: SelfAssessmentSummaryView,
  sa302InterruptView: Sa302InterruptView,
  viewNewsAndUpdatesView: ViewNewsAndUpdatesView,
  viewSaAndItsaMergePageView: ViewSaAndItsaMergePageView,
  viewBreathingSpaceView: ViewBreathingSpaceView,
  enrolmentsHelper: EnrolmentsHelper,
  seissService: SeissService,
  newsAndTilesConfig: NewsAndTilesConfig,
  featureFlagService: FeatureFlagService
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends PertaxBaseController(cc)
    with PaperlessInterruptHelper
    with Logging {

  val saBreadcrumb: Breadcrumb =
    "label.self_assessment" -> routes.InterstitialController.displaySelfAssessment.url ::
      baseBreadcrumb
  private val authenticate: ActionBuilder[UserRequest, AnyContent]   =
    authJourney.authWithPersonalDetails andThen withBreadcrumbAction
      .addBreadcrumb(baseBreadcrumb)
  private val authenticateSa: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails andThen withBreadcrumbAction
      .addBreadcrumb(saBreadcrumb)

  def displayNationalInsurance: Action[AnyContent] = authenticate.async { implicit request =>
    formPartialService.getNationalInsurancePartial.map { p =>
      Ok(
        viewNationalInsuranceInterstitialHomeView(
          formPartial = if (configDecorator.partialUpgradeEnabled) {
            //TODO: FormPartialUpgrade to be deleted. See DDCNL-6008
            FormPartialUpgrade.upgrade(p successfulContentOrEmpty)
          } else {
            p successfulContentOrEmpty
          },
          redirectUrl = currentUrl,
          request.nino
        )
      )
    }
  }

  private def currentUrl(implicit request: Request[AnyContent]) =
    configDecorator.pertaxFrontendHost + request.path

  def displayChildBenefits: Action[AnyContent] = authenticate.async { implicit request =>
    featureFlagService.get(ChildBenefitSingleAccountToggle).map { featureFlag =>
      if (!featureFlag.isEnabled) {
        Ok(
          viewChildBenefitsSummaryInterstitialView(
            redirectUrl = currentUrl,
            taxCreditsEnabled = configDecorator.taxCreditsEnabled
          )
        )
      } else {
        Redirect(routes.InterstitialController.displayChildBenefitsSingleAccountView, MOVED_PERMANENTLY)
      }
    }
  }

  def displayChildBenefitsSingleAccountView: Action[AnyContent] = authenticate.async { implicit request =>
    featureFlagService.get(ChildBenefitSingleAccountToggle).map { featureFlag =>
      if (featureFlag.isEnabled) {
        Ok(
          viewChildBenefitsSummarySingleAccountInterstitialView(
            redirectUrl = currentUrl
          )
        )
      } else {
        errorRenderer.error(UNAUTHORIZED)
      }
    }
  }

  def displaySaAndItsaMergePage: Action[AnyContent] = authenticate.async { implicit request =>
    val saUserType = request.saUserType

    if (
      request.trustedHelper.isEmpty &&
      (enrolmentsHelper.itsaEnrolmentStatus(request.enrolments).isDefined || request.isSa)
    ) {
      for {
        hasSeissClaims    <- seissService.hasClaims(saUserType)
        istaMessageToggle <- featureFlagService.get(ItsaMessageToggle)
      } yield Ok(
        viewSaAndItsaMergePageView(
          redirectUrl = currentUrl(request),
          nextDeadlineTaxYear = (current.currentYear + 1).toString,
          enrolmentsHelper.itsaEnrolmentStatus(request.enrolments).isDefined,
          request.isSa,
          istaMessageToggle.isEnabled,
          hasSeissClaims,
          taxYear = previousAndCurrentTaxYear,
          saUserType
        )
      )
    } else {
      errorRenderer.futureError(UNAUTHORIZED)
    }
  }

  def displaySelfAssessment: Action[AnyContent] = authenticate.async { implicit request =>
    if (request.isSaUserLoggedIntoCorrectAccount) {
      val formPartial = formPartialService.getSelfAssessmentPartial recoverWith { case _ =>
        Future.successful(HtmlPartial.Failure(None, ""))
      }
      val saPartial   = saPartialService.getSaAccountSummary recoverWith { case _ =>
        Future.successful(HtmlPartial.Failure(None, ""))
      }

      for {
        formPartial <- formPartial
        saPartial   <- saPartial
      } yield Ok(
        selfAssessmentSummaryView(
          //TODO: FormPartialUpgrade to be deleted. See DDCNL-6008
          formPartial = if (configDecorator.partialUpgradeEnabled) {
            FormPartialUpgrade.upgrade(formPartial successfulContentOrEmpty)
          } else {
            formPartial successfulContentOrElse Html("")
          },
          saPartial = saPartial successfulContentOrElse Html("")
        )
      )
    } else {
      errorRenderer.futureError(UNAUTHORIZED)
    }

  }

  def displaySa302Interrupt(year: Int): Action[AnyContent] = authenticateSa { implicit request =>
    request.saUserType match {
      case ActivatedOnlineFilerSelfAssessmentUser(saUtr) =>
        Ok(sa302InterruptView(year = previousAndCurrentTaxYearFromGivenYear(year), saUtr = saUtr))
      case _                                             =>
        logger.warn("User had no sa account when one was required")
        errorRenderer.error(UNAUTHORIZED)
    }
  }

  def displayNewsAndUpdates(newsSectionId: String): Action[AnyContent] = authenticate { implicit request =>
    if (configDecorator.isNewsAndUpdatesTileEnabled) {
      val models = newsAndTilesConfig.getNewsAndContentModelList()
      //service to get the dynamic content send the models and get the details from the dynamic list
      Ok(viewNewsAndUpdatesView(redirectUrl = currentUrl, models, newsSectionId))
    } else {
      errorRenderer.error(UNAUTHORIZED)
    }
  }

  def displayBreathingSpaceDetails: Action[AnyContent] = authenticate { implicit request =>
    if (configDecorator.isBreathingSpaceIndicatorEnabled) {
      Ok(viewBreathingSpaceView(redirectUrl = currentUrl))
    } else {
      errorRenderer.error(UNAUTHORIZED)
    }
  }

}
