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
import connectors.TaiConnector
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.{HomeCardGenerator, PaperlessInterruptHelper, RlsInterruptHelper}
import models.BreathingSpaceIndicatorResponse.WithinPeriod
import models.admin.ShowOutageBannerToggle
import play.api.mvc._
import services._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.time.CurrentTaxYear
import util.AlertBannerHelper
import viewmodels.HomeViewModel
import views.html.HomeView

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class HomeController @Inject() (
  paperlessInterruptHelper: PaperlessInterruptHelper,
  taiConnector: TaiConnector,
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

  private def getTaxComponentsOrEmptyList(nino: Nino, year: Int)(implicit
    hc: HeaderCarrier
  ): Future[List[String]] = taiConnector
    .taxComponents(nino, year)
    .fold(
      _ => List.empty,
      taxComponents => taxComponents
    )

  def index: Action[AnyContent] = authenticate.async { implicit request =>
    val saUserType = request.saUserType
    enforceInterrupts {
      for {
        taxComponents           <- getTaxComponentsOrEmptyList(request.helpeeNinoOrElse, current.currentYear)
        breathingSpaceIndicator <- breathingSpaceService.getBreathingSpaceIndicator(request.helpeeNinoOrElse)
        incomeCards             <- homeCardGenerator.getIncomeCards
        atsCard                 <- homeCardGenerator.getATSCard()
        shutteringMessaging     <- featureFlagService.get(ShowOutageBannerToggle)
        alertBannerContent      <- alertBannerHelper.getContent
        personDetails           <- citizenDetailsService.personDetails(request.helpeeNinoOrElse).toOption.value
      } yield {
        val nameToDisplay: Option[String] = Some(personalDetailsNameOrDefault(personDetails))
        val benefitCards                  = homeCardGenerator.getBenefitCards(taxComponents, request.trustedHelper)

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
              nameToDisplay
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
