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

import config.ConfigDecorator
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.interstitials.{InterstitialController, MtdAdvertInterstitialController}
import models.admin.PayeToPegaRedirectToggle
import org.mockito.Mockito.{reset, when}
import play.api.Application
import play.api.http.Status.SEE_OTHER
import play.api.inject.bind
import play.api.mvc.{Request, Result}
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation, status}
import repositories.JourneyCacheRepository
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec}
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future

class RedirectToPayeControllerSpec extends BaseSpec {

  private val mockConfigDecorator: ConfigDecorator                                 = mock[ConfigDecorator]
  private val mockInterstitialController: InterstitialController                   = mock[InterstitialController]
  private val mockMtdAdvertInterstitialController: MtdAdvertInterstitialController =
    mock[MtdAdvertInterstitialController]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthJourney].toInstance(mockAuthJourney),
      bind[ConfigDecorator].toInstance(mockConfigDecorator),
      bind[InterstitialController].toInstance(mockInterstitialController),
      bind[MtdAdvertInterstitialController].toInstance(mockMtdAdvertInterstitialController),
      bind[JourneyCacheRepository].toInstance(mock[JourneyCacheRepository])
    )
    .build()

  private lazy val controller = app.injector.instanceOf[RedirectToPayeController]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthJourney, mockConfigDecorator)
  }

  private def stubAuthJourney(nino: String, trustedHelper: Option[TrustedHelper] = None): Unit =
    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(buildUserRequest(request = request, authNino = Nino(nino), trustedHelper = trustedHelper))
    })

  private def stubFeatureFlag(isEnabled: Boolean): Unit =
    when(mockFeatureFlagService.get(PayeToPegaRedirectToggle))
      .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled)))

  private def stubConfig(
    redirectList: Seq[Int],
    pegaUrl: String = "https://pega.test.redirect",
    taiHost: String = "https://tai.test"
  ): Unit = {
    when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(redirectList)
    when(mockConfigDecorator.payeToPegaRedirectUrl).thenReturn(pegaUrl)
    when(mockConfigDecorator.taiHost).thenReturn(taiHost)
  }

  "redirectToPaye" must {

    "redirect to PEGA when feature flag enabled and NINO matches redirect list and no trusted helper" in {
      val matchingNino = "AA000055A"

      stubFeatureFlag(isEnabled = true)
      stubConfig(redirectList = Seq(5))
      stubAuthJourney(matchingNino)

      val result = controller.redirectToPaye(fakeScaRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe "https://pega.test.redirect"
    }

    "redirect to TAI when feature flag enabled but NINO does not match redirect list" in {
      val nonMatchingNino = "AA000055A"

      stubFeatureFlag(isEnabled = true)
      stubConfig(redirectList = Seq(1))
      stubAuthJourney(nonMatchingNino)

      val result = controller.redirectToPaye(fakeScaRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe "https://tai.test/check-income-tax/what-do-you-want-to-do"
    }

    "redirect to TAI when feature flag enabled and NINO matches but trusted helper is present" in {
      val matchingNino = "AA000055A"

      val trustedHelper = TrustedHelper("principalName", "attorneyName", "returnUrl", Some(matchingNino))

      stubFeatureFlag(isEnabled = true)
      stubConfig(redirectList = Seq(5))
      stubAuthJourney(nino = matchingNino, trustedHelper = Some(trustedHelper))

      val result = controller.redirectToPaye(fakeScaRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe "https://tai.test/check-income-tax/what-do-you-want-to-do"
    }

    "redirect to TAI when feature flag is disabled even if NINO matches redirect list" in {
      val matchingNino = "AA000055A"

      stubFeatureFlag(isEnabled = false)
      stubConfig(redirectList = Seq(5))
      stubAuthJourney(matchingNino)

      val result = controller.redirectToPaye(fakeScaRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe "https://tai.test/check-income-tax/what-do-you-want-to-do"
    }
  }
}
