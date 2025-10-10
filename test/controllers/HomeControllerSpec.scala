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
import connectors.TaiConnector
import controllers.auth.AuthJourney
import controllers.controllershelpers.{HomeCardGenerator, PaperlessInterruptHelper, RlsInterruptHelper}
import models.BreathingSpaceIndicatorResponse
import models.BreathingSpaceIndicatorResponse.WithinPeriod
import models.admin.{GetPersonFromCitizenDetailsToggle, ShowPlannedOutageBannerToggle}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Format
import play.api.mvc.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.twirl.api.Html
import services.*
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

  val mockTaiConnector: TaiConnector                   = mock[TaiConnector]
  val mockBreathingSpaceService: BreathingSpaceService = mock[BreathingSpaceService]
  val mockHomeCardGenerator: HomeCardGenerator         = mock[HomeCardGenerator]
  val mockAlertBannerHelper: AlertBannerHelper         = mock[AlertBannerHelper]

  lazy val appBuilder: GuiceApplicationBuilder = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthJourney].toInstance(fakeAuthJourney),
      bind[RlsInterruptHelper].toInstance(fakeRlsInterruptHelper),
      bind[PaperlessInterruptHelper].toInstance(fakePaperlessInterruptHelper),
      bind[TaiConnector].toInstance(mockTaiConnector),
      bind[BreathingSpaceService].toInstance(mockBreathingSpaceService),
      bind[HomeCardGenerator].toInstance(mockHomeCardGenerator),
      bind[AlertBannerHelper].toInstance(mockAlertBannerHelper)
    )

  private val taxComponents = List("EmployerProvidedServices", "PersonalPensionPayments")

  override implicit lazy val app: Application =
    appBuilder.build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTaiConnector)
    reset(mockBreathingSpaceService)
    reset(mockHomeCardGenerator)
    reset(mockAlertBannerHelper)
    reset(
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

    when(mockTaiConnector.taxComponents[List[String]](any(), any())(any[Format[List[String]]]())(any(), any(), any()))
      .thenReturn(
        EitherT(
          Future.successful[Either[UpstreamErrorResponse, Option[List[String]]]](
            Right[UpstreamErrorResponse, Option[List[String]]](Some(taxComponents))
          )
        )
      )

    when(mockFeatureFlagService.get(ShowPlannedOutageBannerToggle))
      .thenReturn(Future.successful(FeatureFlag(ShowPlannedOutageBannerToggle, isEnabled = false)))

    when(mockFeatureFlagService.get(GetPersonFromCitizenDetailsToggle))
      .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

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

    "Return a Html that is returned as part of Benefit Cards NOT incl tax components when error returned from tax components call" in {
      when(mockTaiConnector.taxComponents[List[String]](any(), any())(any[Format[List[String]]]())(any(), any(), any()))
        .thenReturn(
          EitherT(
            Future.successful[Either[UpstreamErrorResponse, Option[List[String]]]](
              Left[UpstreamErrorResponse, Option[List[String]]](UpstreamErrorResponse("", INTERNAL_SERVER_ERROR))
            )
          )
        )

      val expectedHtmlString = "<div class='TestingForBenefitCards'></div>"
      val expectedHtml: Html = Html(expectedHtmlString)
      when(mockHomeCardGenerator.getBenefitCards(ArgumentMatchers.eq(List.empty), any())(any())).thenReturn(
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
        "A number of services will be unavailable from 10pm on Friday 12 July to 7am Monday 15 July."

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
}
