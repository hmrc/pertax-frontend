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
import connectors.{PreferencesFrontendConnector, TaiConnector, TaxCalculationConnector}
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.{HomeCardGenerator, HomePageCachingHelper, PaperlessInterruptHelper, RlsInterruptHelper}
import models.BreathingSpaceIndicatorResponse.WithinPeriod
import models._
import models.admin.{AlertBannerToggle, NpsShutteringToggle, TaxComponentsToggle, TaxcalcToggle}
import play.api.mvc.{Action, ActionBuilder, AnyContent, MessagesControllerComponents}
import play.twirl.api.Html
import services._
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.CurrentTaxYear
import util.{AlertBannerHelper, Tools}
import viewmodels.HomeViewModel
import views.html.HomeView

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class HomeController @Inject() (
  paperlessInterruptHelper: PaperlessInterruptHelper,
  preferencesFrontendConnector: PreferencesFrontendConnector,
  taiConnector: TaiConnector,
  taxCalculationConnector: TaxCalculationConnector,
  breathingSpaceService: BreathingSpaceService,
  featureFlagService: FeatureFlagService,
  homeCardGenerator: HomeCardGenerator,
  homePageCachingHelper: HomePageCachingHelper,
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  homeView: HomeView,
  seissService: SeissService,
  rlsInterruptHelper: RlsInterruptHelper,
  alertBannerHelper: AlertBannerHelper,
  tools: Tools
)(implicit configDecorator: ConfigDecorator, ec: ExecutionContext)
    extends PertaxBaseController(cc)
    with CurrentTaxYear {

  override def now: () => LocalDate = () => LocalDate.now()

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails

  def index: Action[AnyContent] = authenticate.async { implicit request =>
    val showUserResearchBanner: Future[Boolean] =
      if (configDecorator.bannerHomePageIsEnabled) {
        homePageCachingHelper.hasUserDismissedBanner.map(!_)
      } else {
        Future.successful(false)
      }

    val responses: Future[(TaxComponentsState, Option[TaxYearReconciliation], Option[TaxYearReconciliation])] =
      serviceCallResponses(request.nino, current.currentYear, request.trustedHelper)
    val saUserType                                                                                            = request.saUserType

    rlsInterruptHelper.enforceByRlsStatus(
      showUserResearchBanner flatMap { showUserResearchBanner =>
        paperlessInterruptHelper.enforcePaperlessPreference {
          for {
            (taxSummaryState, taxCalculationStateCyMinusOne, taxCalculationStateCyMinusTwo) <- responses
            _                                                                               <- seissService.hasClaims(saUserType)
            breathingSpaceIndicator                                                         <- breathingSpaceService.getBreathingSpaceIndicator(request.nino).map {
                                                                                                 case WithinPeriod => true
                                                                                                 case _            => false
                                                                                               }
            incomeCards                                                                     <- homeCardGenerator.getIncomeCards(
                                                                                                 taxSummaryState,
                                                                                                 taxCalculationStateCyMinusOne,
                                                                                                 taxCalculationStateCyMinusTwo
                                                                                               )
            shutteringMessaging                                                             <- featureFlagService.get(NpsShutteringToggle)
            paperlessStatus                                                                 <- alertBannerHelper.alertBannerStatus
            alertBannerUrl                                                                  <- alertBannerHelper.alertBannerUrl(paperlessStatus)
          } yield {
            val pensionCards: Seq[Html] = homeCardGenerator.getPensionCards()
            val benefitCards: Seq[Html] =
              homeCardGenerator.getBenefitCards(taxSummaryState.getTaxComponents, request.trustedHelper)

            Ok(
              homeView(
                HomeViewModel(
                  incomeCards,
                  benefitCards,
                  pensionCards,
                  showUserResearchBanner,
                  saUserType,
                  breathingSpaceIndicator,
                  paperlessStatus,
                  alertBannerUrl
                ),
                shutteringMessaging.isEnabled
              )
            )
          }
        }
      }
    )
  }

  private[controllers] def serviceCallResponses(ninoOpt: Option[Nino], year: Int, trustedHelper: Option[TrustedHelper])(
    implicit hc: HeaderCarrier
  ): Future[(TaxComponentsState, Option[TaxYearReconciliation], Option[TaxYearReconciliation])] =
    ninoOpt.fold[Future[(TaxComponentsState, Option[TaxYearReconciliation], Option[TaxYearReconciliation])]](
      Future.successful((TaxComponentsDisabledState, None, None))
    ) { nino =>
      val taxYr = featureFlagService.get(TaxcalcToggle).flatMap { toggle =>
        if (toggle.isEnabled && trustedHelper.isEmpty) {
          taxCalculationConnector.getTaxYearReconciliations(nino).leftMap(_ => List.empty[TaxYearReconciliation]).merge
        } else {
          Future.successful(List.empty[TaxYearReconciliation])
        }
      }

      val taxCalculationStateCyMinusOne = taxYr.map(_.find(_.taxYear == year - 1))
      val taxCalculationStateCyMinusTwo = taxYr.map(_.find(_.taxYear == year - 2))

      val taxSummaryState = featureFlagService.get(TaxComponentsToggle).flatMap { toggle =>
        if (toggle.isEnabled) {
          taiConnector
            .taxComponents(nino, year)
            .fold(
              error =>
                if (error.statusCode == BAD_REQUEST || error.statusCode == NOT_FOUND) {
                  TaxComponentsNotAvailableState
                } else {
                  TaxComponentsUnreachableState
                },
              result => TaxComponentsAvailableState(TaxComponents.fromJsonTaxComponents(result.json))
            )
        } else {
          Future.successful(TaxComponentsDisabledState)
        }
      }

      for {
        taxCalculationStateCyMinusOne <- taxCalculationStateCyMinusOne
        taxCalculationStateCyMinusTwo <- taxCalculationStateCyMinusTwo
        taxSummaryState               <- taxSummaryState
      } yield (taxSummaryState, taxCalculationStateCyMinusOne, taxCalculationStateCyMinusTwo)
    }
}
