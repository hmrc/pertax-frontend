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

import cats.data.EitherT
import controllers.auth.AuthJourney
import controllers.controllershelpers.{HomeCardGenerator, PaperlessInterruptHelper, RlsInterruptHelper}
import models.BreathingSpaceIndicatorResponse
import models.BreathingSpaceIndicatorResponse.WithinPeriod
import models.admin.ShowOutageBannerToggle
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services._
import testUtils.fakes.{FakeAuthJourney, FakePaperlessInterruptHelper, FakeRlsInterruptHelper}
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http.{HeaderNames, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import util.AlertBannerHelper

import scala.concurrent.Future

class HomeControllerSpec extends BaseSpec with WireMockHelper {
  val fakeAuthJourney              = new FakeAuthJourney
  val fakeRlsInterruptHelper       = new FakeRlsInterruptHelper
  val fakePaperlessInterruptHelper = new FakePaperlessInterruptHelper

  val mockTaiService: TaxComponentService              = mock[TaxComponentService]
  val mockBreathingSpaceService: BreathingSpaceService = mock[BreathingSpaceService]
  val mockHomeCardGenerator: HomeCardGenerator         = mock[HomeCardGenerator]
  val mockAlertBannerHelper: AlertBannerHelper         = mock[AlertBannerHelper]

  lazy val appBuilder: GuiceApplicationBuilder = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthJourney].toInstance(fakeAuthJourney),
      bind[RlsInterruptHelper].toInstance(fakeRlsInterruptHelper),
      bind[PaperlessInterruptHelper].toInstance(fakePaperlessInterruptHelper),
      bind[TaxComponentService].toInstance(mockTaiService),
      bind[BreathingSpaceService].toInstance(mockBreathingSpaceService),
      bind[HomeCardGenerator].toInstance(mockHomeCardGenerator),
      bind[AlertBannerHelper].toInstance(mockAlertBannerHelper)
    )

  override implicit lazy val app: Application =
    appBuilder.build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    org.mockito.MockitoSugar.reset(
      mockTaiService,
      mockBreathingSpaceService,
      mockHomeCardGenerator,
      mockAlertBannerHelper,
      mockFeatureFlagService
    )

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
    when(mockAlertBannerHelper.getContent(any(), any(), any())).thenReturn(
      Future.successful(List.empty)
    )
    when(mockTaiService.get(any(), any())(any())).thenReturn(
      EitherT(
        Future.successful[Either[UpstreamErrorResponse, List[String]]](
          Right[UpstreamErrorResponse, List[String]](List("EmployerProvidedServices", "PersonalPensionPayments"))
        )
      )
    )

    when(mockFeatureFlagService.get(ShowOutageBannerToggle))
      .thenReturn(Future.successful(FeatureFlag(ShowOutageBannerToggle, isEnabled = false)))

  }

  def currentRequest[A]: Request[A] =
    FakeRequest()
      .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
      .asInstanceOf[Request[A]]

  "Calling HomeController.index" must {
    "Return a Html that is returned as part of Benefit Cards" in {
      val expectedHtmlString = "<div class='TestingForBenefitCards'></div>"
      val expectedHtml: Html = Html(expectedHtmlString)
      when(mockHomeCardGenerator.getBenefitCards(any(), any())(any())).thenReturn(
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
        "A number of services will be unavailable from 10pm on Friday 12 July to 7am Monday 15 July."

      when(mockFeatureFlagService.get(ShowOutageBannerToggle))
        .thenReturn(Future.successful(FeatureFlag(ShowOutageBannerToggle, isEnabled = true)))

      val appLocal: Application = appBuilder.build()

      val controller: HomeController = appLocal.injector.instanceOf[HomeController]
      val result: Future[Result]     = controller.index()(currentRequest)
      status(result) mustBe OK
      assert(contentAsString(result).replaceAll("\\s", "").contains(expectedHtmlString.replaceAll("\\s", "")))
    }

    "Shuttering is not displayed if toggled off" in {
      val expectedHtmlString =
        "A number of services will be unavailable from 10pm on Friday 12 July to 7am Monday 15 July."

      when(mockFeatureFlagService.get(ShowOutageBannerToggle))
        .thenReturn(Future.successful(FeatureFlag(ShowOutageBannerToggle, isEnabled = false)))

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
  }
}
