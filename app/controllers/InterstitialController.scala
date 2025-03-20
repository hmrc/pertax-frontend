/*
 * Copyright 2024 HM Revenue & Customs
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
import config.{BannerTcsServiceClosure, ConfigDecorator, NewsAndTilesConfig}
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import error.ErrorRenderer
import models._
import models.admin.{BreathingSpaceIndicatorToggle, ShowOutageBannerToggle}
import play.api.Logging
import play.api.mvc._
import play.twirl.api.Html
import services.partials.{FormPartialService, SaPartialService}
import services.{CitizenDetailsService, SeissService}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.partials.HtmlPartial
import util.DateTimeTools._
import util.{EnrolmentsHelper, FormPartialUpgrade}
import views.html.interstitial._
import views.html.selfassessment.Sa302InterruptView
import views.html.{SelfAssessmentSummaryView, ShutteringView}

import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class InterstitialController @Inject() (
  val formPartialService: FormPartialService,
  val saPartialService: SaPartialService,
  authJourney: AuthJourney,
  withBreadcrumbAction: WithBreadcrumbAction,
  cc: MessagesControllerComponents,
  errorRenderer: ErrorRenderer,
  viewChildBenefitsSummarySingleAccountInterstitialView: ViewChildBenefitsSummarySingleAccountInterstitialView,
  selfAssessmentSummaryView: SelfAssessmentSummaryView,
  sa302InterruptView: Sa302InterruptView,
  viewNewsAndUpdatesView: ViewNewsAndUpdatesView,
  viewItsaMergePageView: ViewItsaMergePageView,
  viewBreathingSpaceView: ViewBreathingSpaceView,
  shutteringView: ShutteringView,
  taxCreditsAddressInterstitialView: TaxCreditsAddressInterstitialView,
  taxCreditsTransitionInformationInterstitialView: TaxCreditsTransitionInformationInterstitialView,
  taxCreditsEndedInformationInterstitialView: TaxCreditsEndedInformationInterstitialView,
  enrolmentsHelper: EnrolmentsHelper,
  seissService: SeissService,
  newsAndTilesConfig: NewsAndTilesConfig,
  featureFlagService: FeatureFlagService,
  citizenDetailsService: CitizenDetailsService,
  viewNISPView: ViewNISPView,
  selfAssessmentRegistrationPageView: SelfAssessmentRegistrationPageView
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends PertaxBaseController(cc)
    with Logging {

  private val saBreadcrumb: Breadcrumb =
    "label.self_assessment" -> routes.InterstitialController.displaySelfAssessment.url ::
      baseBreadcrumb
  private val authenticate: ActionBuilder[UserRequest, AnyContent] = {
    authJourney.authWithPersonalDetails andThen withBreadcrumbAction
      .addBreadcrumb(baseBreadcrumb)
  }
  private val authenticateSa: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails andThen withBreadcrumbAction
      .addBreadcrumb(saBreadcrumb)

  def displayNationalInsurance: Action[AnyContent] = authenticate.async {
    Future.successful(Redirect(controllers.routes.InterstitialController.displayNISP.url))
  }

  def displayNISP: Action[AnyContent] = authenticate.async { implicit request =>
    for {
      nispPartial <- formPartialService.getNISPPartial
      nino        <- citizenDetailsService
                       .personDetails(request.authNino)
                       .fold(_ => Some(request.authNino), personDetails => personDetails.person.nino)
    } yield Ok(
      viewNISPView(
        formPartial = if (configDecorator.partialUpgradeEnabled) {
          FormPartialUpgrade.upgrade(nispPartial successfulContentOrEmpty)
        } else {
          nispPartial successfulContentOrEmpty
        },
        nino
      )
    )
  }

  def displayChildBenefits: Action[AnyContent] = authenticate {
    Redirect(routes.InterstitialController.displayChildBenefitsSingleAccountView, MOVED_PERMANENTLY)
  }

  def displayChildBenefitsSingleAccountView: Action[AnyContent] = authenticate { implicit request =>
    Ok(
      viewChildBenefitsSummarySingleAccountInterstitialView()
    )
  }

  def displaySaRegistrationPage: Action[AnyContent] = authenticate { implicit request =>
    val isHelperOrEnrolledOrSa = request.trustedHelper.isDefined || enrolmentsHelper
      .itsaEnrolmentStatus(request.enrolments)
      .isDefined || request.isSa
    if (isHelperOrEnrolledOrSa || !configDecorator.pegaSaRegistrationEnabled) {
      // Temporarily restricting access based on pegaEnabled, this condition can be removed in future
      errorRenderer.error(UNAUTHORIZED)
    } else {
      Ok(selfAssessmentRegistrationPageView(configDecorator.pegaSaRegistrationUrl))
    }
  }

  def displayItsaMergePage: Action[AnyContent] = authenticate.async { implicit request =>
    val saUserType = request.saUserType
    if (
      enrolmentsHelper
        .itsaEnrolmentStatus(request.enrolments)
        .isDefined && request.trustedHelper.isEmpty && request.isSa
    ) {
      seissService.hasClaims(saUserType).map { hasSeissClaims =>
        Ok(
          viewItsaMergePageView(
            request.isSa,
            hasSeissClaims,
            saUserType
          )
        )
      }
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
      if (models.nonEmpty) {
        Ok(viewNewsAndUpdatesView(models, newsSectionId))
      } else {
        Redirect(routes.HomeController.index)
      }
    } else {
      errorRenderer.error(UNAUTHORIZED)
    }
  }

  def displayBreathingSpaceDetails: Action[AnyContent] = authenticate.async { implicit request =>
    featureFlagService.get(BreathingSpaceIndicatorToggle).flatMap { featureFlag =>
      if (featureFlag.isEnabled) {
        Future.successful(Ok(viewBreathingSpaceView()))
      } else {
        Future.successful(errorRenderer.error(UNAUTHORIZED))
      }
    }
  }

  def displayTaxCreditsInterstitial: Action[AnyContent] = authenticate { implicit request =>
    configDecorator.featureBannerTcsServiceClosure match {
      case BannerTcsServiceClosure.Enabled
          if ZonedDateTime.now.compareTo(configDecorator.tcsFrontendEndDateTime) <= 0 =>
        Ok(taxCreditsAddressInterstitialView())
      case BannerTcsServiceClosure.Enabled =>
        Redirect(controllers.routes.InterstitialController.displayTaxCreditsEndedInformationInterstitialView)
      case _                               => errorRenderer.error(UNAUTHORIZED)
    }
  }

  def displayShutteringPage: Action[AnyContent] = authenticate.async { implicit request =>
    featureFlagService.get(ShowOutageBannerToggle).flatMap { featureFlag =>
      if (featureFlag.isEnabled) {
        Future.successful(Ok(shutteringView()))
      } else {
        Future.successful(Redirect(routes.HomeController.index))
      }
    }
  }

  def displayTaxCreditsTransitionInformationInterstitialView: Action[AnyContent] = Action {
    implicit request: MessagesRequest[AnyContent] =>
      configDecorator.featureBannerTcsServiceClosure match {
        case BannerTcsServiceClosure.Enabled
            if ZonedDateTime.now.compareTo(configDecorator.tcsFrontendEndDateTime) <= 0 =>
          Ok(taxCreditsTransitionInformationInterstitialView())
        case BannerTcsServiceClosure.Enabled =>
          Redirect(controllers.routes.InterstitialController.displayTaxCreditsEndedInformationInterstitialView)
        case _                               => errorRenderer.error(UNAUTHORIZED)

      }
  }
  def displayTaxCreditsEndedInformationInterstitialView: Action[AnyContent]      = Action { implicit request =>
    if (configDecorator.featureBannerTcsServiceClosure == BannerTcsServiceClosure.Enabled) {
      Ok(taxCreditsEndedInformationInterstitialView())
    } else {
      errorRenderer.error(UNAUTHORIZED)
    }
  }

}
