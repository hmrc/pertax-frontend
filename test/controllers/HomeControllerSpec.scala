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

import connectors.FandFConnector
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.{HomeCardGenerator, PaperlessInterruptHelper, RlsInterruptHelper}
import models.BreathingSpaceIndicatorResponse
import models.BreathingSpaceIndicatorResponse.WithinPeriod
import models.admin.{GetPersonFromCitizenDetailsToggle, ShowPlannedOutageBannerToggle}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.twirl.api.Html
import services.*
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.fakes.{FakeAuthJourney, FakePaperlessInterruptHelper, FakeRlsInterruptHelper}
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import util.AlertBannerHelper

import scala.concurrent.Future

class HomeControllerSpec extends BaseSpec with WireMockHelper {
  val fakeAuthJourney              = new FakeAuthJourney
  val fakeRlsInterruptHelper       = new FakeRlsInterruptHelper
  val fakePaperlessInterruptHelper = new FakePaperlessInterruptHelper

  val mockBreathingSpaceService: BreathingSpaceService = mock[BreathingSpaceService]
  val mockHomeCardGenerator: HomeCardGenerator         = mock[HomeCardGenerator]
  val mockAlertBannerHelper: AlertBannerHelper         = mock[AlertBannerHelper]
  val mockTaiService: TaiService                       = mock[TaiService]
  val mockFandfConnector: FandFConnector               = mock[FandFConnector]

  lazy val appBuilder: GuiceApplicationBuilder = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthJourney].toInstance(fakeAuthJourney),
      bind[RlsInterruptHelper].toInstance(fakeRlsInterruptHelper),
      bind[PaperlessInterruptHelper].toInstance(fakePaperlessInterruptHelper),
      bind[BreathingSpaceService].toInstance(mockBreathingSpaceService),
      bind[HomeCardGenerator].toInstance(mockHomeCardGenerator),
      bind[AlertBannerHelper].toInstance(mockAlertBannerHelper),
      bind[TaiService].toInstance(mockTaiService),
      bind[FandFConnector].toInstance(mockFandfConnector)
    )

  private val taxComponents = List("EmployerProvidedServices", "PersonalPensionPayments")

  override implicit lazy val app: Application =
    appBuilder.build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTaiService)
    reset(mockBreathingSpaceService)
    reset(mockHomeCardGenerator)
    reset(mockAlertBannerHelper)
    reset(mockFeatureFlagService)

    when(mockBreathingSpaceService.getBreathingSpaceIndicator(any())(any(), any()))
      .thenReturn(Future.successful(WithinPeriod))
    when(mockHomeCardGenerator.getIncomeCards(any(), any())).thenReturn(
      Future.successful(Seq.empty)
    )
    when(mockHomeCardGenerator.getATSCard()(any(), any())).thenReturn(
      Future.successful(Seq.empty)
    )
    when(mockHomeCardGenerator.getBenefitCards(any(), any())(any())).thenReturn(
      List.empty
    )
    when(mockHomeCardGenerator.getTrustedHelpersCard()(any())).thenReturn(
      Html("<div class='trusted-helpers-card'></div>")
    )

    when(mockAlertBannerHelper.getContent(any(), any(), any())).thenReturn(
      Future.successful(List.empty)
    )

    when(mockTaiService.getTaxComponentsList(any(), any())(any(), any()))
      .thenReturn(Future.successful(taxComponents))

    when(mockFeatureFlagService.get(ShowPlannedOutageBannerToggle))
      .thenReturn(Future.successful(FeatureFlag(ShowPlannedOutageBannerToggle, isEnabled = false)))

    when(mockFeatureFlagService.get(GetPersonFromCitizenDetailsToggle))
      .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

    when(mockFandfConnector.showFandfBanner(any())(any(), any()))
      .thenReturn(Future.successful(false))
  }

  def currentRequest[A]: Request[A] =
    FakeRequest()
      .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
      .asInstanceOf[Request[A]]

  "Calling HomeController.index" must {
    "Return a Html that is returned as part of Benefit Cards incl tax components" in {
      val expectedHtmlString = "<div class='TestingForBenefitCards'></div>"
      val expectedHtml: Html = Html(expectedHtmlString)
      when(mockHomeCardGenerator.getBenefitCards(ArgumentMatchers.eq(taxComponents), any())(any())).thenReturn(
        List(expectedHtml)
      )

      val appLocal: Application = appBuilder.build()

      val controller: HomeController = appLocal.injector.instanceOf[HomeController]
      val result: Future[Result]     = controller.index()(currentRequest)
      status(result) mustBe OK
      assert(contentAsString(result).replaceAll("\\s", "").contains(expectedHtmlString.replaceAll("\\s", "")))
    }

    "Return a Breathing space if that is returned within period" in {
      val expectedHtmlString =
        "<a class=\"govuk-link govuk-link--no-visited-state\" href=\"/personal-account/breathing-space\">"
      when(mockBreathingSpaceService.getBreathingSpaceIndicator(any())(any(), any())).thenReturn(
        Future.successful(BreathingSpaceIndicatorResponse.WithinPeriod)
      )

      val appLocal: Application = appBuilder.build()

      val controller: HomeController = appLocal.injector.instanceOf[HomeController]
      val result: Future[Result]     = controller.index()(currentRequest)
      status(result) mustBe OK
      assert(contentAsString(result).replaceAll("\\s", "").contains(expectedHtmlString.replaceAll("\\s", "")))
    }

    "Does not return a Breathing space if that is returned within period" in {
      val expectedHtmlString =
        "<a class=\"govuk-link govuk-link--no-visited-state\" href=\"/personal-account/breathing-space\">"
      when(mockBreathingSpaceService.getBreathingSpaceIndicator(any())(any(), any())).thenReturn(
        Future.successful(BreathingSpaceIndicatorResponse.OutOfPeriod)
      )

      val appLocal: Application = appBuilder.build()

      val controller: HomeController = appLocal.injector.instanceOf[HomeController]
      val result: Future[Result]     = controller.index()(currentRequest)
      status(result) mustBe OK
      assert(!contentAsString(result).replaceAll("\\s", "").contains(expectedHtmlString.replaceAll("\\s", "")))
    }

    List(
      BreathingSpaceIndicatorResponse.OutOfPeriod,
      BreathingSpaceIndicatorResponse.NotFound,
      BreathingSpaceIndicatorResponse.StatusUnknown
    ).foreach { breathingSpaceResponse =>
      s"Does not return a Breathing space if that is returned $breathingSpaceResponse" in {
        val expectedHtmlString =
          "<a class=\"govuk-link govuk-link--no-visited-state\" href=\"/personal-account/breathing-space\">"
        when(mockBreathingSpaceService.getBreathingSpaceIndicator(any())(any(), any())).thenReturn(
          Future.successful(breathingSpaceResponse)
        )

        val appLocal: Application = appBuilder.build()

        val controller: HomeController = appLocal.injector.instanceOf[HomeController]
        val result: Future[Result]     = controller.index()(currentRequest)
        status(result) mustBe OK
        assert(!contentAsString(result).replaceAll("\\s", "").contains(expectedHtmlString.replaceAll("\\s", "")))
      }
    }

    "Shuttering is displayed if toggled on" in {
      val expectedHtmlString =
        "A number of services will be unavailable from"

      when(mockFeatureFlagService.get(ShowPlannedOutageBannerToggle))
        .thenReturn(Future.successful(FeatureFlag(ShowPlannedOutageBannerToggle, isEnabled = true)))

      val appLocal: Application = appBuilder.build()

      val controller: HomeController = appLocal.injector.instanceOf[HomeController]
      val result: Future[Result]     = controller.index()(currentRequest)
      status(result) mustBe OK
      assert(contentAsString(result).replaceAll("\\s", "").contains(expectedHtmlString.replaceAll("\\s", "")))
    }

    "Shuttering is not displayed if toggled off" in {
      val expectedHtmlString =
        "A number of services will be unavailable from"

      when(mockFeatureFlagService.get(ShowPlannedOutageBannerToggle))
        .thenReturn(Future.successful(FeatureFlag(ShowPlannedOutageBannerToggle, isEnabled = false)))

      val appLocal: Application = appBuilder.build()

      val controller: HomeController = appLocal.injector.instanceOf[HomeController]
      val result: Future[Result]     = controller.index()(currentRequest)
      status(result) mustBe OK
      assert(!contentAsString(result).replaceAll("\\s", "").contains(expectedHtmlString.replaceAll("\\s", "")))
    }

    "Alert Banner content is displayed as returned from the alertBannerContent" in {
      val expectedHtmlString = "<div class='alertBannerContent'></div>"
      val expectedHtml: Html = Html(expectedHtmlString)

      when(mockAlertBannerHelper.getContent(any(), any(), any()))
        .thenReturn(Future.successful(List(expectedHtml)))

      val appLocal: Application = appBuilder.build()

      val controller: HomeController = appLocal.injector.instanceOf[HomeController]
      val result: Future[Result]     = controller.index()(currentRequest)
      status(result) mustBe OK
      assert(contentAsString(result).replaceAll("\\s", "").contains(expectedHtmlString.replaceAll("\\s", "")))
    }

    "Trusted helpers card is displayed" in {
      val expectedHtmlString = "<div class='trusted-helpers-card'></div>"
      when(mockHomeCardGenerator.getTrustedHelpersCard()(any()))
        .thenReturn(Html(expectedHtmlString))

      val appLocal: Application = appBuilder.build()

      val controller: HomeController = appLocal.injector.instanceOf[HomeController]
      val result: Future[Result]     = controller.index()(currentRequest)
      status(result) mustBe OK
      assert(contentAsString(result).replaceAll("\\s", "").contains(expectedHtmlString.replaceAll("\\s", "")))
    }

    "Trusted helpers card is hidden when acting as a trusted helper" in {
      val th = TrustedHelper("principalName", "attorneyName", "returnUrl", Some(generatedTrustedHelperNino.nino))

      val appLocal: Application =
        localGuiceApplicationBuilder()
          .overrides(
            bind[AuthJourney].toInstance(new AuthJourney {
              override def authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
                new testUtils.ActionBuilderFixture {
                  override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result])
                    : Future[Result] =
                    block(
                      buildUserRequest(
                        request = request,
                        trustedHelper = Some(th)
                      )
                    )
                }
            }),
            bind[RlsInterruptHelper].toInstance(fakeRlsInterruptHelper),
            bind[PaperlessInterruptHelper].toInstance(fakePaperlessInterruptHelper),
            bind[TaiService].toInstance(mockTaiService),
            bind[BreathingSpaceService].toInstance(mockBreathingSpaceService),
            bind[HomeCardGenerator].toInstance(mockHomeCardGenerator),
            bind[AlertBannerHelper].toInstance(mockAlertBannerHelper),
            bind[FandFConnector].toInstance(mockFandfConnector)
          )
          .build()

      val controller: HomeController = appLocal.injector.instanceOf[HomeController]

      val result = controller.index()(FakeRequest())
      status(result) mustBe OK

      val html = contentAsString(result).replaceAll("\\s", "")
      html must not include "<divclass='trusted-helpers-card'></div>"

      verify(mockHomeCardGenerator, times(0)).getTrustedHelpersCard()(any())
    }

  }

  "Alert Banner content is not displayed if empty" in {
    when(mockAlertBannerHelper.getContent(any(), any(), any()))
      .thenReturn(Future.successful(List.empty))

    val appLocal: Application      = appBuilder.build()
    val controller: HomeController = appLocal.injector.instanceOf[HomeController]
    val result: Future[Result]     = controller.index()(currentRequest)
    status(result) mustBe OK
    assert(!contentAsString(result).contains("alertBannerContent"))
  }

  "Fandf Banner content is displayed if connector returns true" in {
    when(mockFandfConnector.showFandfBanner(any())(any(), any()))
      .thenReturn(Future.successful(true))

    val appLocal: Application      = appBuilder.build()
    val controller: HomeController = appLocal.injector.instanceOf[HomeController]
    val result: Future[Result]     = controller.index()(currentRequest)
    status(result) mustBe OK
    assert(contentAsString(result).contains("Your trusted helper relationship ends at midnight"))
  }
}
