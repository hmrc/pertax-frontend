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
import controllers.controllershelpers.{HomeCardGenerator, HomeOptionsGenerator, PaperlessInterruptHelper, RlsInterruptHelper}
import models.BreathingSpaceIndicatorResponse.WithinPeriod
import models.SelfAssessmentUser
import models.admin.{HomePageNewLayoutToggle, ShowPlannedOutageBannerToggle}
import play.api.mvc.*
import services.*
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.time.CurrentTaxYear
import util.AlertBannerHelper
import viewmodels.{HomeViewModel, NewHomeViewModel}
import views.html.{HomeView, NewHomeView}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class HomeController @Inject() (
  paperlessInterruptHelper: PaperlessInterruptHelper,
  taiService: TaiService,
  breathingSpaceService: BreathingSpaceService,
  featureFlagService: FeatureFlagService,
  citizenDetailsService: CitizenDetailsService,
  homeCardGenerator: HomeCardGenerator,
  homeOptionsGenerator: HomeOptionsGenerator,
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  homeView: HomeView,
  newHomeView: NewHomeView,
  rlsInterruptHelper: RlsInterruptHelper,
  alertBannerHelper: AlertBannerHelper
)(implicit ec: ExecutionContext)
    extends PertaxBaseController(cc)
    with CurrentTaxYear {

  override def now: () => LocalDate = () => LocalDate.now()

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails

  def newHomePage(implicit request: UserRequest[AnyContent]): Future[Result] = {

    val nino: Nino = request.helpeeNinoOrElse

    val utr: Option[String] = request.saUserType match {
      case saUser: SelfAssessmentUser => Some(saUser.saUtr.utr)
      case _                          => None
    }

    enforceInterrupts {
      val fBreathingSpaceIndicator = breathingSpaceService.getBreathingSpaceIndicator(nino)
      val fListOfTasks             = homeOptionsGenerator.getListOfTasks
      val fCurrentTaxesAndBenefits = homeOptionsGenerator.getCurrentTaxesAndBenefits
      val fOtherTaxesAndBenefits   = homeOptionsGenerator.getOtherTaxesAndBenefits
      val fShutteringMessaging     = featureFlagService.get(ShowPlannedOutageBannerToggle)
      val fAlertBannerContent      = alertBannerHelper.getContent
      val fEitherPersonDetails     = citizenDetailsService.personDetails(nino).value

      for {
        breathingSpaceIndicator <- fBreathingSpaceIndicator
        listOfTasks             <- fListOfTasks
        currentTaxesAndBenefit  <- fCurrentTaxesAndBenefits
        otherTaxesAndBenefits   <- fOtherTaxesAndBenefits
        shutteringMessaging     <- fShutteringMessaging
        alertBannerContent      <- fAlertBannerContent
        eitherPersonDetails     <- fEitherPersonDetails
      } yield {
        val personDetailsOpt = eitherPersonDetails.toOption.flatten
        val nameToDisplay    = Some(personalDetailsNameOrDefault(personDetailsOpt))

        Ok(
          newHomeView(
            NewHomeViewModel(
              listOfTasks,
              currentTaxesAndBenefit,
              otherTaxesAndBenefits,
              homeOptionsGenerator.getLatestNewsAndUpdatesCard(),
              showUserResearchBanner = false,
              utr,
              breathingSpaceIndicator = breathingSpaceIndicator == WithinPeriod,
              alertBannerContent = alertBannerContent,
              name = nameToDisplay
            ),
            shutteringMessaging.isEnabled
          )
        )
      }
    }
  }

  def oldHomePage(implicit request: UserRequest[AnyContent]) = {
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

  def index: Action[AnyContent] = authenticate.async { implicit request =>
    featureFlagService.get(HomePageNewLayoutToggle).flatMap { toggle =>
      if (toggle.isEnabled) newHomePage
      else oldHomePage
    }
  }

  private def enforceInterrupts(block: => Future[Result])(implicit request: UserRequest[AnyContent]): Future[Result] =
    rlsInterruptHelper.enforceByRlsStatus(
      paperlessInterruptHelper.enforcePaperlessPreference(block)
    )
}
