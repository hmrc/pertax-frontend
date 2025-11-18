/*
 * Copyright 2025 HM Revenue & Customs
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
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.{HomeCardGenerator, PaperlessInterruptHelper, RlsInterruptHelper}
import models.BreathingSpaceIndicatorResponse.WithinPeriod
import models.admin.ShowPlannedOutageBannerToggle
import play.api.mvc._
import services._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.time.CurrentTaxYear
import util.AlertBannerHelper
import viewmodels.HomeViewModel
import views.html.HomeView

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class HomeController @Inject() (
  paperlessInterruptHelper: PaperlessInterruptHelper,
  taiService: TaiService,
  breathingSpaceService: BreathingSpaceService,
  featureFlagService: FeatureFlagService,
  citizenDetailsService: CitizenDetailsService,
  homeCardGenerator: HomeCardGenerator,
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  homeView: HomeView,
  rlsInterruptHelper: RlsInterruptHelper,
  alertBannerHelper: AlertBannerHelper
)(implicit ec: ExecutionContext)
    extends PertaxBaseController(cc)
    with CurrentTaxYear {

  override def now: () => LocalDate = () => LocalDate.now()

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails

  def index: Action[AnyContent] = authenticate.async { implicit request =>
    val saUserType = request.saUserType

    val nino: Nino   = request.helpeeNinoOrElse
    val taxYear: Int = current.currentYear

    enforceInterrupts {
      val fTaxComponents           = taiService.getTaxComponentsList(nino, taxYear)
      val fBreathingSpaceIndicator = breathingSpaceService.getBreathingSpaceIndicator(nino)
      val fIncomeCards             = homeCardGenerator.getIncomeCards
      val fAtsCard                 = homeCardGenerator.getATSCard()
      val fShutteringMessaging     = featureFlagService.get(ShowPlannedOutageBannerToggle)
      val fAlertBannerContent      = alertBannerHelper.getContent
      val fEitherPersonDetails     = citizenDetailsService.personDetails(nino).value

      for {
        taxComponents           <- fTaxComponents
        breathingSpaceIndicator <- fBreathingSpaceIndicator
        incomeCards             <- fIncomeCards
        atsCard                 <- fAtsCard
        shutteringMessaging     <- fShutteringMessaging
        alertBannerContent      <- fAlertBannerContent
        eitherPersonDetails     <- fEitherPersonDetails
      } yield {
        val personDetailsOpt = eitherPersonDetails.toOption.flatten
        val nameToDisplay    = Some(personalDetailsNameOrDefault(personDetailsOpt))

        val benefitCards       = homeCardGenerator.getBenefitCards(taxComponents, request.trustedHelper)
        val trustedHelpersCard = if (request.trustedHelper.isDefined) {
          None
        } else {
          Some(homeCardGenerator.getTrustedHelpersCard())
        }

        Ok(
          homeView(
            HomeViewModel(
              incomeCards,
              benefitCards,
              atsCard,
              showUserResearchBanner = false,
              saUserType,
              breathingSpaceIndicator = breathingSpaceIndicator == WithinPeriod,
              alertBannerContent,
              nameToDisplay,
              trustedHelpersCard
            ),
            shutteringMessaging.isEnabled
          )
        )
      }
    }
  }

  private def enforceInterrupts(block: => Future[Result])(implicit request: UserRequest[AnyContent]): Future[Result] =
    rlsInterruptHelper.enforceByRlsStatus(
      paperlessInterruptHelper.enforcePaperlessPreference(block)
    )
}
