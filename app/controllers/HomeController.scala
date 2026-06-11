/*
 * Copyright 2026 HM Revenue & Customs
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
import controllers.controllershelpers.{HomeOptionsGenerator, PaperlessInterruptHelper, RlsInterruptHelper}
import models.BreathingSpaceIndicatorResponse.WithinPeriod
import models.SelfAssessmentUser
import models.admin.HomePagePersonalisationToggle
import play.api.mvc.*
import services.*
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.time.CurrentTaxYear
import util.AlertBannerHelper
import viewmodels.{AlertBanner, HomeViewModel, NewsAndUpdates, PtapAlertBanner, PtapHomeViewModel, PtapNewsAndUpdates}
import views.html.{HomeView, PtapHomeView}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class HomeController @Inject() (
  paperlessInterruptHelper: PaperlessInterruptHelper,
  breathingSpaceService: BreathingSpaceService,
  featureFlagService: FeatureFlagService,
  citizenDetailsService: CitizenDetailsService,
  homePageServicesProvider: HomePageServicesProvider,
  tasksService: TasksService,
  homeOptionsGenerator: HomeOptionsGenerator,
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  homeView: HomeView,
  pTapHomeView: PtapHomeView,
  rlsInterruptHelper: RlsInterruptHelper,
  alertBannerHelper: AlertBannerHelper
)(implicit val ec: ExecutionContext)
    extends PertaxBaseController(cc)
    with CurrentTaxYear {

  override def now: () => LocalDate = () => LocalDate.now()

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails

  def homePageTab(tab: String): Action[AnyContent]                                                                   = {
    val validTabs: Set[String] = Set("/task", "/activity", "/tax", "/news", "/support")
    val currentTab: String     = if (validTabs.contains(tab)) tab else "/task"
    // also need to add redirect for STANDARD /personal-account  path

    index(Some(currentTab))
  }
  private def personalisationHomePage(currentTab: String)(implicit request: UserRequest[AnyContent]): Future[Result] = {

    val nino: Nino = request.helpeeNinoOrElse

    val utr: Option[String] = request.saUserType match {
      case saUser: SelfAssessmentUser => Some(saUser.saUtr.utr)
      case _                          => None
    }

    enforceInterrupts {
      val fBreathingSpaceIndicator = breathingSpaceService.getBreathingSpaceIndicator(nino)
      val fListOfTasks             = tasksService.getListOfTasks
      val fEitherPersonDetails     = citizenDetailsService.personDetails(nino).value

      for {
        breathingSpaceIndicator <- fBreathingSpaceIndicator
        listOfTasks             <- fListOfTasks
        eitherPersonDetails     <- fEitherPersonDetails
        alertBannerContent      <- alertBannerHelper.getContent(eitherPersonDetails.toOption.flatten)
      } yield {
        val personDetailsOpt = eitherPersonDetails.toOption.flatten
        val nameToDisplay    = Some(personalDetailsNameOrDefault(personDetailsOpt))

        Ok(
          pTapHomeView(
            PtapHomeViewModel(
              listOfTasks,
              homeOptionsGenerator.getLatestNewsAndUpdatesCard().map(PtapNewsAndUpdates.apply),
              showUserResearchBanner = false,
              utr,
              breathingSpaceIndicator = breathingSpaceIndicator == WithinPeriod,
              alertBannerContent = alertBannerContent.map(PtapAlertBanner.apply),
              name = nameToDisplay,
              currentTab = currentTab
            )
          )
        )
      }
    }
  }

  private def newHomePage(implicit request: UserRequest[AnyContent]): Future[Result] = {

    val nino: Nino = request.helpeeNinoOrElse

    val utr: Option[String] = request.saUserType match {
      case saUser: SelfAssessmentUser => Some(saUser.saUtr.utr)
      case _                          => None
    }

    enforceInterrupts {
      val fBreathingSpaceIndicator = breathingSpaceService.getBreathingSpaceIndicator(nino)
      val fListOfTasks             = tasksService.getListOfTasks
      val fHomePageServices        = homePageServicesProvider.getHomePageServices
      val fEitherPersonDetails     = citizenDetailsService.personDetails(nino).value

      for {
        breathingSpaceIndicator <- fBreathingSpaceIndicator
        listOfTasks             <- fListOfTasks
        homePageServices        <- fHomePageServices
        eitherPersonDetails     <- fEitherPersonDetails
        alertBannerContent      <- alertBannerHelper.getContent(eitherPersonDetails.toOption.flatten)
      } yield {
        val personDetailsOpt = eitherPersonDetails.toOption.flatten
        val nameToDisplay    = Some(personalDetailsNameOrDefault(personDetailsOpt))

        Ok(
          homeView(
            HomeViewModel(
              listOfTasks,
              homeOptionsGenerator.getLatestNewsAndUpdatesCard().map(NewsAndUpdates.apply),
              showUserResearchBanner = false,
              utr,
              breathingSpaceIndicator = breathingSpaceIndicator == WithinPeriod,
              alertBannerContent = alertBannerContent.map(AlertBanner.apply),
              name = nameToDisplay,
              myServices = homePageServices.myServices,
              otherServices = homePageServices.otherServices
            )
          )
        )
      }
    }
  }

  def index(currentTab: Option[String] = None): Action[AnyContent] = authenticate.async { implicit request =>
    featureFlagService.get(HomePagePersonalisationToggle).flatMap { toggle =>
      if (toggle.isEnabled) {
        currentTab match
          case Some(tab) => personalisationHomePage(tab)
          case None      => personalisationHomePage("/task")
      } else {
        newHomePage
      }
    }
  }

  private def enforceInterrupts(block: => Future[Result])(implicit request: UserRequest[AnyContent]): Future[Result] =
    rlsInterruptHelper.enforceByRlsStatus(
      paperlessInterruptHelper.enforcePaperlessPreference(block)
    )
}
