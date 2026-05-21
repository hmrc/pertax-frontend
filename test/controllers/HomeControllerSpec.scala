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
import models.admin.{GetPersonFromCitizenDetailsToggle, ShowPlannedOutageBannerToggle}
import models.{BreathingSpaceIndicatorResponse, HomePageServices}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
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
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.fakes.{FakeAuthJourney, FakePaperlessInterruptHelper, FakeRlsInterruptHelper}
import testUtils.{BaseSpec, CitizenDetailsFixtures, WireMockHelper}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{HeaderNames, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import util.AlertBannerHelper

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
    reset(mockConfigDecorator)
    reset(mockCitizenDetailsService)

    when(mockBreathingSpaceService.getBreathingSpaceIndicator(any())(any(), any()))
      .thenReturn(Future.successful(WithinPeriod))

    when(mockHomeOptionsGenerator.getLatestNewsAndUpdatesCard()(any[Messages], any[UserRequest[AnyContent]]))
      .thenReturn(None)

    when(mockAlertBannerHelper.getContent(any(), any())(any(), any(), any()))
      .thenReturn(Future.successful(None))

    when(mockTasksService.getListOfTasks(any(), any()))
      .thenReturn(Future.successful(Seq.empty))

    when(mockTaiService.getTaxComponentsList(any(), any())(any(), any()))
      .thenReturn(Future.successful(taxComponents))

    when(mockFeatureFlagService.get(ShowPlannedOutageBannerToggle))
      .thenReturn(Future.successful(FeatureFlag(ShowPlannedOutageBannerToggle, isEnabled = false)))

    when(mockFeatureFlagService.get(GetPersonFromCitizenDetailsToggle))
      .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

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

      when(mockAlertBannerHelper.getContent(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Some(expectedHtml)))

      val appLocal   = appBuilder.build()
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(currentRequest)

      status(result) mustBe OK
      contentAsString(result).replaceAll("\\s", "") must include(expectedHtmlString.replaceAll("\\s", ""))
    }

    "Alert Banner content is not displayed if empty" in {
      when(mockAlertBannerHelper.getContent(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(None))

      val appLocal   = appBuilder.build()
      val controller = appLocal.injector.instanceOf[HomeController]
      val result     = controller.index()(currentRequest)

      status(result) mustBe OK
      contentAsString(result) must not include "alertBannerContent"
    }
  }
}
