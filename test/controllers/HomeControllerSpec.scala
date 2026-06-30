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

import cats.data.EitherT
import config.ConfigDecorator
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.{HomeOptionsGenerator, PaperlessInterruptHelper, RlsInterruptHelper}
import models.BreathingSpaceIndicatorResponse.WithinPeriod
import models.admin.{GetPersonFromCitizenDetailsToggle, HomePagePersonalisationToggle, ShowPlannedOutageBannerToggle}
import models.{BreathingSpaceIndicatorResponse, HomePageServices, MyService, OtherService}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, when}
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.twirl.api.Html
import repositories.JourneyCacheRepository
import services.*
import testUtils.HmrcCardModelFixtures
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.fakes.{FakeAuthJourney, FakePaperlessInterruptHelper, FakeRlsInterruptHelper}
import testUtils.{BaseSpec, CitizenDetailsFixtures, WireMockHelper}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.sca.models.TrustedHelper
import uk.gov.hmrc.http.{HeaderNames, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import util.AlertBannerHelper
import viewmodels.TabEnum

import scala.concurrent.Future

class HomeControllerSpec extends BaseSpec with WireMockHelper with CitizenDetailsFixtures {

  val fakeAuthJourney              = new FakeAuthJourney
  val fakeRlsInterruptHelper       = new FakeRlsInterruptHelper
  val fakePaperlessInterruptHelper = new FakePaperlessInterruptHelper

  val mockBreathingSpaceService: BreathingSpaceService       = mock[BreathingSpaceService]
  val mockHomeOptionsGenerator: HomeOptionsGenerator         = mock[HomeOptionsGenerator]
  val mockAlertBannerHelper: AlertBannerHelper               = mock[AlertBannerHelper]
  val mockTaiService: TaiService                             = mock[TaiService]
  val mockHomePageServicesProvider: HomePageServicesProvider = mock[HomePageServicesProvider]
  val mockTasksService: TasksService                         = mock[TasksService]
  val mockTabContentService: TabContentService               = mock[TabContentService]
  val mockConfigDecorator: ConfigDecorator                   = mock[ConfigDecorator]
  val mockCitizenDetailsService: CitizenDetailsService       = mock[CitizenDetailsService]

  lazy val appBuilder: GuiceApplicationBuilder =
    localGuiceApplicationBuilder()
      .overrides(
        bind[AuthJourney].toInstance(fakeAuthJourney),
        bind[RlsInterruptHelper].toInstance(fakeRlsInterruptHelper),
        bind[PaperlessInterruptHelper].toInstance(fakePaperlessInterruptHelper),
        bind[BreathingSpaceService].toInstance(mockBreathingSpaceService),
        bind[HomeOptionsGenerator].toInstance(mockHomeOptionsGenerator),
        bind[AlertBannerHelper].toInstance(mockAlertBannerHelper),
        bind[TaiService].toInstance(mockTaiService),
        bind[HomePageServicesProvider].toInstance(mockHomePageServicesProvider),
        bind[TasksService].toInstance(mockTasksService),
        bind[TabContentService].toInstance(mockTabContentService),
        bind[ConfigDecorator].toInstance(mockConfigDecorator),
        bind[CitizenDetailsService].toInstance(mockCitizenDetailsService),
        bind[JourneyCacheRepository].toInstance(mock[JourneyCacheRepository])
      )

  private val taxComponents = List("EmployerProvidedServices", "PersonalPensionPayments")

  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(mockTaiService)
    reset(mockBreathingSpaceService)
    reset(mockHomeOptionsGenerator)
    reset(mockAlertBannerHelper)
    reset(mockFeatureFlagService)
    reset(mockHomePageServicesProvider)
    reset(mockTasksService)
    reset(mockTabContentService)
    reset(mockConfigDecorator)
    reset(mockCitizenDetailsService)

    when(mockConfigDecorator.ptapHomepageNinoRolloutLastNumericDigits)
      .thenReturn(Seq(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))

    when(mockBreathingSpaceService.getBreathingSpaceIndicator(any())(any(), any()))
      .thenReturn(Future.successful(WithinPeriod))

    when(mockHomeOptionsGenerator.getLatestNewsAndUpdatesCard()(any[Messages], any[UserRequest[AnyContent]]))
      .thenReturn(None)

    when(mockAlertBannerHelper.getContent(any())(any(), any(), any()))
      .thenReturn(Future.successful(None))

    when(mockTasksService.getListOfTasks(any(), any()))
      .thenReturn(Future.successful(Seq.empty))

    when(mockTabContentService.getTaskAndTabCards(any[TabEnum]())(any(), any()))
      .thenReturn(Future.successful(TabContentCards(Seq.empty, Seq.empty)))

    when(mockTaiService.getTaxComponentsList(any(), any())(any(), any()))
      .thenReturn(Future.successful(taxComponents))

    when(mockFeatureFlagService.get(ShowPlannedOutageBannerToggle))
      .thenReturn(Future.successful(FeatureFlag(ShowPlannedOutageBannerToggle, isEnabled = false)))

    when(mockFeatureFlagService.get(GetPersonFromCitizenDetailsToggle))
      .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(HomePagePersonalisationToggle))
      .thenReturn(Future.successful(FeatureFlag(HomePagePersonalisationToggle, isEnabled = false)))

    when(mockConfigDecorator.getFeedbackSurveyUrl(any()))
      .thenReturn("/personal-account/signed-out")

    when(mockHomePageServicesProvider.getHomePageServices(any(), any(), any()))
      .thenReturn(Future.successful(HomePageServices(Seq.empty)))

    when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
      .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](None))
  }

  def currentRequest[A]: Request[A] =
    FakeRequest()
      .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
      .asInstanceOf[Request[A]]

  private def appWithAuthJourney(authJourney: AuthJourney): Application =
    localGuiceApplicationBuilder()
      .overrides(
        bind[AuthJourney].toInstance(authJourney),
        bind[RlsInterruptHelper].toInstance(fakeRlsInterruptHelper),
        bind[PaperlessInterruptHelper].toInstance(fakePaperlessInterruptHelper),
        bind[BreathingSpaceService].toInstance(mockBreathingSpaceService),
        bind[HomeOptionsGenerator].toInstance(mockHomeOptionsGenerator),
        bind[AlertBannerHelper].toInstance(mockAlertBannerHelper),
        bind[TaiService].toInstance(mockTaiService),
        bind[HomePageServicesProvider].toInstance(mockHomePageServicesProvider),
        bind[TasksService].toInstance(mockTasksService),
        bind[TabContentService].toInstance(mockTabContentService),
        bind[ConfigDecorator].toInstance(mockConfigDecorator),
        bind[CitizenDetailsService].toInstance(mockCitizenDetailsService),
        bind[JourneyCacheRepository].toInstance(mock[JourneyCacheRepository])
      )
      .build()

  "Calling HomeController.index" must {

    "Return new home page design" in {
      val request = FakeRequest("GET", "/personal-account")
        .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[AnyContent]]

      val generatedNino: Nino = new Generator().nextNino

      val appLocal =
        appWithAuthJourney(
          new AuthJourney {
            override def authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
              new testUtils.ActionBuilderFixture {
                override def invokeBlock[A](
                  request: Request[A],
                  block: UserRequest[A] => Future[Result]
                ): Future[Result] =
                  block(
                    buildUserRequest(
                      request = request,
                      authNino = generatedNino,
                      trustedHelper = None
                    )
                  )
              }
          }
        )

      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(request)

      status(result) mustBe OK

      val content = Jsoup.parse(contentAsString(result))
      content.getElementById("taxes-and-benefits-heading") must not be null
    }

    "Render to the tasks tab without redirecting when HomePagePersonalisationToggle is true and ptap param is true" in {
      val path    = "/personal-account?ptap=true"
      val request = FakeRequest("GET", path)
        .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[AnyContent]]

      when(mockFeatureFlagService.get(HomePagePersonalisationToggle))
        .thenReturn(Future.successful(FeatureFlag(HomePagePersonalisationToggle, isEnabled = true)))

      val appLocal   = appBuilder.build()
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(request)

      status(result) mustBe OK
      redirectLocation(result) mustBe None

      val content = Jsoup.parse(contentAsString(result))
      content.select("nav.x-govuk-secondary-navigation").size mustBe 1
      content.getElementById("taxes-and-benefits-heading") mustBe null

    }

    "Render the non Personalised home page when ptap param is absent and HomePagePersonalisationToggle is true" in {
      val request = FakeRequest("GET", "/personal-account")
        .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[AnyContent]]

      when(mockFeatureFlagService.get(HomePagePersonalisationToggle))
        .thenReturn(Future.successful(FeatureFlag(HomePagePersonalisationToggle, isEnabled = true)))

      val appLocal   = appBuilder.build()
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(request)

      status(result) mustBe OK

      val content = Jsoup.parse(contentAsString(result))
      content.select("nav.x-govuk-secondary-navigation").size mustBe 0
      content.getElementById("taxes-and-benefits-heading") must not be null
    }

    "Render the non Personalised home page when ptap param is present but HomePagePersonalisationToggle is false" in {
      val path    = "/personal-account?ptap=true"
      val request = FakeRequest("GET", path)
        .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[AnyContent]]

      when(mockFeatureFlagService.get(HomePagePersonalisationToggle))
        .thenReturn(Future.successful(FeatureFlag(HomePagePersonalisationToggle, isEnabled = false)))

      val appLocal   = appBuilder.build()
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(request)

      status(result) mustBe OK

      val content = Jsoup.parse(contentAsString(result))
      content.select("nav.x-govuk-secondary-navigation").size mustBe 0
      content.getElementById("taxes-and-benefits-heading") must not be null
    }

    "fetch tab content once for the default Task tab and derive badge count from task cards" in {
      val path    = "/personal-account?ptap=true"
      val request = FakeRequest("GET", path)
        .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[AnyContent]]

      when(mockFeatureFlagService.get(HomePagePersonalisationToggle))
        .thenReturn(Future.successful(FeatureFlag(HomePagePersonalisationToggle, isEnabled = true)))

      when(mockTabContentService.getTaskAndTabCards(any[TabEnum]())(any(), any()))
        .thenReturn(
          Future.successful(
            TabContentCards(HmrcCardModelFixtures.taskCards, HmrcCardModelFixtures.taskCards)
          )
        )

      val appLocal   = appBuilder.build()
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(request)

      status(result) mustBe OK
      verify(mockTabContentService).getTaskAndTabCards(any[TabEnum]())(any(), any())

      val content = Jsoup.parse(contentAsString(result))
      content.select(".x-govuk-secondary-navigation__badge").text() mustBe "2"
      content.getElementById("tab-content-header").text() mustBe "Your tasks"
      content.text() must include("You owe tax for 2023-24")
      content.text() must not include "Tax code change"
    }

    "fetch tab content once for the Activity tab and render only activity cards" in {
      val request = FakeRequest("GET", "/personal-account/recent-activity?ptap=true")
        .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[AnyContent]]

      when(mockFeatureFlagService.get(HomePagePersonalisationToggle))
        .thenReturn(Future.successful(FeatureFlag(HomePagePersonalisationToggle, isEnabled = true)))

      when(mockTabContentService.getTaskAndTabCards(any[TabEnum]())(any(), any()))
        .thenReturn(
          Future.successful(
            TabContentCards(HmrcCardModelFixtures.taskCards, HmrcCardModelFixtures.activityCards)
          )
        )

      val appLocal   = appBuilder.build()
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.homePageTab("recent-activity")(request)

      status(result) mustBe OK
      verify(mockTabContentService).getTaskAndTabCards(any[TabEnum]())(any(), any())

      val content = Jsoup.parse(contentAsString(result))
      content.select(".x-govuk-secondary-navigation__badge").text() mustBe "2"
      content.getElementById("tab-content-header").text() mustBe "Recent activity"
      content.text() must include("Tax code change")
      content.text() must not include "You owe tax for 2023-24"
    }

    "fetch tab content once for a non-card tab without rendering task or activity cards" in {
      val request = FakeRequest("GET", "/personal-account/hmrc-news?ptap=true")
        .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[AnyContent]]

      when(mockFeatureFlagService.get(HomePagePersonalisationToggle))
        .thenReturn(Future.successful(FeatureFlag(HomePagePersonalisationToggle, isEnabled = true)))

      when(mockTabContentService.getTaskAndTabCards(any[TabEnum]())(any(), any()))
        .thenReturn(
          Future.successful(
            TabContentCards(HmrcCardModelFixtures.taskCards, Seq.empty)
          )
        )

      val appLocal   = appBuilder.build()
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.homePageTab("hmrc-news")(request)

      status(result) mustBe OK
      verify(mockTabContentService).getTaskAndTabCards(any[TabEnum]())(any(), any())

      val content = Jsoup.parse(contentAsString(result))
      content.select(".x-govuk-secondary-navigation__badge").text() mustBe "2"
      content.getElementById("tab-content-header") mustBe null
      content.select(".hmrc-card").size() mustBe 0
      content.text() must not include "You owe tax for 2023-24"
      content.text() must not include "Tax code change"
    }

    "not render the tasks tab when HomePagePersonalisationToggle is false" in {
      val request = FakeRequest("GET", "/personal-account")
        .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[AnyContent]]

      when(mockFeatureFlagService.get(HomePagePersonalisationToggle))
        .thenReturn(Future.successful(FeatureFlag(HomePagePersonalisationToggle, isEnabled = false)))

      val appLocal   = appBuilder.build()
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(request)

      status(result) mustBe OK
      redirectLocation(result) mustBe None

      val content = Jsoup.parse(contentAsString(result))
      content.getElementById("taxes-and-benefits-heading") must not be null
      content.select("nav.x-govuk-secondary-navigation").size mustBe 0
    }

    "render the new design when HomePagePersonalisationToggle is true and ptap param is true and the NINO is eligible" in {
      // NINO AA000009A: last numeric digit is 9; configure rollout list to include 9
      val eligibleNino = Nino("AA000009A")
      val request      = FakeRequest("GET", "/personal-account?ptap=true")
        .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[AnyContent]]

      when(mockFeatureFlagService.get(HomePagePersonalisationToggle))
        .thenReturn(Future.successful(FeatureFlag(HomePagePersonalisationToggle, isEnabled = true)))

      when(mockConfigDecorator.ptapHomepageNinoRolloutLastNumericDigits)
        .thenReturn(Seq(9))

      val appLocal   = appWithAuthJourney(
        new AuthJourney {
          override def authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
            new testUtils.ActionBuilderFixture {
              override def invokeBlock[A](
                request: Request[A],
                block: UserRequest[A] => Future[Result]
              ): Future[Result] =
                block(buildUserRequest(request = request, authNino = eligibleNino, trustedHelper = None))
            }
        }
      )
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(request)

      status(result) mustBe OK

      val content = Jsoup.parse(contentAsString(result))
      content.select("nav.x-govuk-secondary-navigation").size mustBe 1
      content.getElementById("taxes-and-benefits-heading") mustBe null
    }

    "render the old design when HomePagePersonalisationToggle is true but the NINO is not eligible" in {
      // NINO AA000008A: last numeric digit is 8; configure rollout list to exclude 8
      val ineligibleNino = Nino("AA000008A")
      val request        = FakeRequest("GET", "/personal-account")
        .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[AnyContent]]

      when(mockFeatureFlagService.get(HomePagePersonalisationToggle))
        .thenReturn(Future.successful(FeatureFlag(HomePagePersonalisationToggle, isEnabled = true)))

      when(mockConfigDecorator.ptapHomepageNinoRolloutLastNumericDigits)
        .thenReturn(Seq(9))

      val appLocal   = appWithAuthJourney(
        new AuthJourney {
          override def authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
            new testUtils.ActionBuilderFixture {
              override def invokeBlock[A](
                request: Request[A],
                block: UserRequest[A] => Future[Result]
              ): Future[Result] =
                block(buildUserRequest(request = request, authNino = ineligibleNino, trustedHelper = None))
            }
        }
      )
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(request)

      status(result) mustBe OK

      val content = Jsoup.parse(contentAsString(result))
      content.getElementById("taxes-and-benefits-heading") must not be null
      content.select("nav.x-govuk-secondary-navigation").size mustBe 0
    }

    "render the old design when HomePagePersonalisationToggle is false even if the NINO is eligible" in {
      // NINO AA000009A: last numeric digit is 9; rollout list includes 9 — but toggle is OFF
      val eligibleNino = Nino("AA000009A")
      val request      = FakeRequest("GET", "/personal-account")
        .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[AnyContent]]

      when(mockFeatureFlagService.get(HomePagePersonalisationToggle))
        .thenReturn(Future.successful(FeatureFlag(HomePagePersonalisationToggle, isEnabled = false)))

      when(mockConfigDecorator.ptapHomepageNinoRolloutLastNumericDigits)
        .thenReturn(Seq(9))

      val appLocal   = appWithAuthJourney(
        new AuthJourney {
          override def authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
            new testUtils.ActionBuilderFixture {
              override def invokeBlock[A](
                request: Request[A],
                block: UserRequest[A] => Future[Result]
              ): Future[Result] =
                block(buildUserRequest(request = request, authNino = eligibleNino, trustedHelper = None))
            }
        }
      )
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(request)

      status(result) mustBe OK

      val content = Jsoup.parse(contentAsString(result))
      content.getElementById("taxes-and-benefits-heading") must not be null
      content.select("nav.x-govuk-secondary-navigation").size mustBe 0
    }

    "render the new design using the trusted-helper principal NINO when it is eligible" in {
      // Auth NINO AA000008A (last digit 8, ineligible) but principal NINO AA000009A (last digit 9, eligible)
      // helpeeNinoOrElse resolves to the principal NINO — new design must be shown
      val authNino      = Nino("AA000008A")
      val principalNino = Nino("AA000009A")
      val request       = FakeRequest("GET", "/personal-account?ptap=true")
        .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[AnyContent]]

      when(mockFeatureFlagService.get(HomePagePersonalisationToggle))
        .thenReturn(Future.successful(FeatureFlag(HomePagePersonalisationToggle, isEnabled = true)))

      when(mockConfigDecorator.ptapHomepageNinoRolloutLastNumericDigits)
        .thenReturn(Seq(9))

      val appLocal   = appWithAuthJourney(
        new AuthJourney {
          override def authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
            new testUtils.ActionBuilderFixture {
              override def invokeBlock[A](
                request: Request[A],
                block: UserRequest[A] => Future[Result]
              ): Future[Result] =
                block(
                  buildUserRequest(
                    request = request,
                    authNino = authNino,
                    trustedHelper = Some(TrustedHelper("principal", "attorney", "/return", Some(principalNino.nino)))
                  )
                )
            }
        }
      )
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(request)

      status(result) mustBe OK

      val content = Jsoup.parse(contentAsString(result))
      content.select("nav.x-govuk-secondary-navigation").size mustBe 1
      content.getElementById("taxes-and-benefits-heading") mustBe null
    }

    "render the new design falling back to auth NINO when trusted-helper has no principal NINO" in {
      // Trusted helper present but principalNino is None — helpeeNinoOrElse falls back to authNino
      // authNino AA000009A (last digit 9) is eligible — new design must be shown
      val authNino = Nino("AA000009A")
      val request  = FakeRequest("GET", "/personal-account?ptap=true")
        .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[AnyContent]]

      when(mockFeatureFlagService.get(HomePagePersonalisationToggle))
        .thenReturn(Future.successful(FeatureFlag(HomePagePersonalisationToggle, isEnabled = true)))

      when(mockConfigDecorator.ptapHomepageNinoRolloutLastNumericDigits)
        .thenReturn(Seq(9))

      val appLocal   = appWithAuthJourney(
        new AuthJourney {
          override def authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
            new testUtils.ActionBuilderFixture {
              override def invokeBlock[A](
                request: Request[A],
                block: UserRequest[A] => Future[Result]
              ): Future[Result] =
                block(
                  buildUserRequest(
                    request = request,
                    authNino = authNino,
                    trustedHelper = Some(TrustedHelper("principal", "attorney", "/return", None))
                  )
                )
            }
        }
      )
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(request)

      status(result) mustBe OK

      val content = Jsoup.parse(contentAsString(result))
      content.select("nav.x-govuk-secondary-navigation").size mustBe 1
      content.getElementById("taxes-and-benefits-heading") mustBe null
    }

    "Return a Breathing space if that is returned within period" in {
      val expectedHtmlString =
        "<a class=\"govuk-link govuk-link--no-visited-state\" href=\"/personal-account/breathing-space\">"

      when(mockBreathingSpaceService.getBreathingSpaceIndicator(any())(any(), any()))
        .thenReturn(Future.successful(BreathingSpaceIndicatorResponse.WithinPeriod))

      val appLocal   = appBuilder.build()
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(currentRequest)

      status(result) mustBe OK
      contentAsString(result).replaceAll("\\s", "") must include(expectedHtmlString.replaceAll("\\s", ""))
    }

    List(
      BreathingSpaceIndicatorResponse.OutOfPeriod,
      BreathingSpaceIndicatorResponse.NotFound,
      BreathingSpaceIndicatorResponse.StatusUnknown
    ).foreach { breathingSpaceResponse =>
      s"Does not return a Breathing space if that is returned $breathingSpaceResponse" in {
        val expectedHtmlString =
          "<a class=\"govuk-link govuk-link--no-visited-state\" href=\"/personal-account/breathing-space\">"

        when(mockBreathingSpaceService.getBreathingSpaceIndicator(any())(any(), any()))
          .thenReturn(Future.successful(breathingSpaceResponse))

        val appLocal   = appBuilder.build()
        val controller = appLocal.injector.instanceOf[HomeController]
        val result     = controller.index()(currentRequest)

        status(result) mustBe OK
        contentAsString(result).replaceAll("\\s", "") must not include expectedHtmlString.replaceAll("\\s", "")
      }
    }

    "Alert Banner content is displayed as returned from the alertBannerContent" in {
      val expectedHtmlString = "<div class='alertBannerContent'></div>"
      val expectedHtml       = Html(expectedHtmlString)

      when(mockAlertBannerHelper.getContent(any())(any(), any(), any()))
        .thenReturn(Future.successful(Some(expectedHtml)))

      val appLocal   = appBuilder.build()
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(currentRequest)

      status(result) mustBe OK
      contentAsString(result).replaceAll("\\s", "") must include(expectedHtmlString.replaceAll("\\s", ""))
    }

    "Alert Banner content is not displayed if empty" in {
      when(mockAlertBannerHelper.getContent(any())(any(), any(), any()))
        .thenReturn(Future.successful(None))

      val appLocal   = appBuilder.build()
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(currentRequest)

      status(result) mustBe OK
      contentAsString(result) must not include "alertBannerContent"
    }

    "Render the Tax tab with personalised services when accessing /personal-account/taxes-and-benefits" in {
      val request = FakeRequest("GET", "/personal-account/taxes-and-benefits?ptap=true")
        .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[AnyContent]]

      when(mockFeatureFlagService.get(HomePagePersonalisationToggle))
        .thenReturn(Future.successful(FeatureFlag(HomePagePersonalisationToggle, isEnabled = true)))

      val payeService = MyService(
        "Pay As You Earn (PAYE)",
        Some("/paye"),
        None,
        id = Some("paye")
      )

      val childBenefitService = OtherService(
        "Child Benefit",
        "/child-benefit",
        id = Some("child-benefit")
      )

      when(mockHomePageServicesProvider.getHomePageServices(any(), any(), any()))
        .thenReturn(Future.successful(HomePageServices(Seq(payeService, childBenefitService))))

      val appLocal   = appBuilder.build()
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.homePageTab("taxes-and-benefits")(request)

      status(result) mustBe OK

      val content = Jsoup.parse(contentAsString(result))
      content.select("nav.x-govuk-secondary-navigation").size mustBe 1
      content.getElementById("my-services-heading") must not be null
      content.select("div.hmrc-card").size mustBe 2
      content.getElementsContainingText("Pay As You Earn (PAYE)").attr("href") mustBe "/paye"
      content.getElementsContainingText("Child Benefit").attr("href") mustBe "/child-benefit"
    }
  }
}
