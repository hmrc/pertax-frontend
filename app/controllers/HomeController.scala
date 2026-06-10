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
import viewmodels.{AlertBanner, HomeViewModel, NewsAndUpdates, PtapAlertBanner, PtapHomeTab, PtapHomeViewModel, PtapNewsAndUpdates}
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
  alertBannerHelper: AlertBannerHelper,
  tabContentService: TabContentService
)(implicit val ec: ExecutionContext)
    extends PertaxBaseController(cc)
    with CurrentTaxYear {

  override def now: () => LocalDate = () => LocalDate.now()

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails

  def homePageTab(tab: String)                                                                                   = authenticate.async { implicit request =>
    personalisationHomePageTab(tab)
  }
  private def personalisationHomePageTab(tab: String)(implicit request: UserRequest[AnyContent]): Future[Result] = {
    val currentTab: PtapHomeTab = PtapHomeTab.fromKey(tab)

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
        val taskCount        = tabContentService.getTaskCount(listOfTasks)

        Ok(
          pTapHomeView(
            PtapHomeViewModel(
              tasks = listOfTasks,
              newsAndUpdates = homeOptionsGenerator.getLatestNewsAndUpdatesCard().map(PtapNewsAndUpdates.apply),
              showUserResearchBanner = false,
              saUtr = utr,
              breathingSpaceIndicator = breathingSpaceIndicator == WithinPeriod,
              alertBannerContent = alertBannerContent.map(PtapAlertBanner.apply),
              name = nameToDisplay,
              currentTab = currentTab.key,
              secondaryNav = tabContentService.getSecondaryNavModel(currentTab, taskCount),
              tabContent = tabContentService.getTabContentModel(currentTab, taskCount)
            )
          )
        )
      }
    }
  }
  private def personalisationHomePage: Future[Result]                                                            =
    Future.successful(Redirect(routes.HomeController.homePageTab(PtapHomeTab.default.key)))

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

  def index: Action[AnyContent] = authenticate.async { implicit request =>
    featureFlagService.get(HomePagePersonalisationToggle).flatMap { toggle =>
      if (toggle.isEnabled) {
        personalisationHomePage
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
