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
import error.ErrorRenderer
import models.BreathingSpaceIndicatorResponse.WithinPeriod
import models.SelfAssessmentUser
import models.admin.HomePagePersonalisationToggle
import play.api.i18n.Messages
import play.api.mvc.*
import play.twirl.api.Html
import services.*
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.time.CurrentTaxYear
import util.AlertBannerHelper
import viewmodels.{AlertBanner, CardContainerModel, HomeViewModel, NewsAndUpdates, PtapAlertBanner, PtapHomeViewModel, PtapNewsAndUpdates, SecondaryNavModel, TabEnum, TabModel}
import viewmodels.TabEnum.*
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
  tabContentService: TabContentService,
  homeOptionsGenerator: HomeOptionsGenerator,
  authJourney: AuthJourney,
  cc: MessagesControllerComponents,
  homeView: HomeView,
  pTapHomeView: PtapHomeView,
  rlsInterruptHelper: RlsInterruptHelper,
  alertBannerHelper: AlertBannerHelper,
  errorRenderer: ErrorRenderer
)(implicit val ec: ExecutionContext)
    extends PertaxBaseController(cc)
    with CurrentTaxYear {

  override def now: () => LocalDate = () => LocalDate.now()

  private val authenticate: ActionBuilder[UserRequest, AnyContent] =
    authJourney.authWithPersonalDetails

  def homePageTab(tab: String) =
    authenticate.async { implicit request =>
      featureFlagService.get(HomePagePersonalisationToggle).flatMap { toggle =>
        if (toggle.isEnabled) {
          personalisationHomePageTab(tab)
        } else {
          newHomePage
        }
      }
    }

  private def personalisationHomePageTab(tab: String)(implicit
    request: UserRequest[AnyContent]
  ): Future[Result] =
    withValidTab(tab) { currentTab =>
      val nino: Nino = request.helpeeNinoOrElse

      val utr: Option[String] = request.saUserType match {
        case saUser: SelfAssessmentUser => Some(saUser.saUtr.utr)
        case _                          => None
      }

      enforceInterrupts {
        val fBreathingSpaceIndicator = breathingSpaceService.getBreathingSpaceIndicator(nino)
        val fEitherPersonDetails     = citizenDetailsService.personDetails(nino).value
        val fTabContentCards         = tabContentService.getTaskAndTabCards(currentTab)

        for {
          breathingSpaceIndicator <- fBreathingSpaceIndicator
          eitherPersonDetails     <- fEitherPersonDetails
          alertBannerContent      <- alertBannerHelper.getContent(eitherPersonDetails.toOption.flatten)
          tabContentCards         <- fTabContentCards
        } yield {
          val personDetailsOpt = eitherPersonDetails.toOption.flatten
          val nameToDisplay    = Some(personalDetailsNameOrDefault(personDetailsOpt))

          val taskCount    = tabContentCards.taskCount
          val secondaryNav = buildSecondaryNav(currentTab, taskCount)
          val tabContent   = currentTab.cardContainerHeading.map { heading =>
            CardContainerModel(
              emptyView = Html(""),
              header = Some(heading),
              cards = tabContentCards.tabCards,
              headerId = Some("tab-content-header")
            )
          }.toList

          Ok(
            pTapHomeView(
              PtapHomeViewModel(
                homeOptionsGenerator.getLatestNewsAndUpdatesCard().map(PtapNewsAndUpdates.apply),
                showUserResearchBanner = false,
                utr,
                breathingSpaceIndicator = breathingSpaceIndicator == WithinPeriod,
                alertBannerContent = alertBannerContent.map(PtapAlertBanner.apply),
                name = nameToDisplay,
                secondaryNav = secondaryNav,
                tabContent = tabContent,
                showSupportView = currentTab == Support
              )
            )
          )
        }
      }
    }

  private def buildSecondaryNav(currentTab: TabEnum, taskCount: Int)(implicit messages: Messages): SecondaryNavModel =
    SecondaryNavModel(
      classes = Some("govuk-!-margin-bottom-6"),
      items = Seq(
        TabModel(
          text = messages("ptap.support.uya.p2.sub"),
          href = Task.href(),
          current = currentTab == Task,
          notificationCount = if (taskCount > 0) Some(taskCount) else None
        ),
        TabModel(
          text = messages("ptap.support.uya.p3.sub"),
          href = Activity.href(),
          current = currentTab == Activity
        ),
        TabModel(
          text = messages("ptap.support.uya.p4.sub"),
          href = Tax.href(),
          current = currentTab == Tax
        ),
        TabModel(
          text = messages("ptap.support.uya.p5.sub"),
          href = News.href(),
          current = currentTab == News
        ),
        TabModel(
          text = messages("ptap.support.uya.p6.sub"),
          href = Support.href(),
          current = currentTab == Support
        )
      )
    )

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
        personalisationHomePageTab(Task.name)
      } else {
        newHomePage
      }
    }
  }

  private def withValidTab(tab: String)(block: TabEnum => Future[Result])(implicit
    request: UserRequest[AnyContent]
  ): Future[Result] =
    val currentTab: Option[TabEnum] = tab match {
      case Task.name     => Some(Task)
      case Activity.name => Some(Activity)
      case Tax.name      => Some(Tax)
      case News.name     => Some(News)
      case Support.name  => Some(Support)
      case _             => None
    }
    currentTab match {
      case Some(value) => block(value)
      case None        => errorRenderer.futureError(NOT_FOUND)
    }

  private def enforceInterrupts(block: => Future[Result])(implicit request: UserRequest[AnyContent]): Future[Result] =
    rlsInterruptHelper.enforceByRlsStatus(
      paperlessInterruptHelper.enforcePaperlessPreference(block)
    )
}
