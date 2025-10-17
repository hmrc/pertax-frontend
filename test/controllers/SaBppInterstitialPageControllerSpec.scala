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
import play.api.http.Status.OK
import play.api.mvc.{AnyContentAsEmpty, MessagesControllerComponents, Result}
import play.api.test.Helpers.*
import play.api.test.FakeRequest
import testUtils.fakes.FakeAuthJourney
import views.html.interstitial.SPPInterstitialView
import testUtils.BaseSpec

import scala.concurrent.Future

class SaBppInterstitialPageControllerSpec extends BaseSpec {

  val fakeAuthJourney = new FakeAuthJourney

  private lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")

  val saBppInterstitialPage: SPPInterstitialView = inject[SPPInterstitialView]

  object TestSaBppInterstitialPageController
      extends SaBppInterstitialPageController(
        fakeAuthJourney,
        cc = inject[MessagesControllerComponents],
        saBppInterstitialPage,
        inject[ConfigDecorator]
      )

  "SaBppInterstitialPageController" when {
    "saBppInterstitialPageController.onPageLoad" must {
      "return a sa bpp interstitial page view with parameter pta-sa" in {

        val result: Future[Result] =
          TestSaBppInterstitialPageController.onPageLoad(fakeRequest)
        status(result) mustBe OK

        contentAsString(result) must include("Spread the cost of your Self Assessment with a Direct Debit")
        contentAsString(result) must include("Pay an overdue Self Assessment bill")
        contentAsString(result) must include("Pay my next Self Assessment bill in advance")
      }
    }

    "saBppInterstitialPageController.onSubmit called with form parameters - empty" must {
      "return same page with error message" in {

        val result: Future[Result] = TestSaBppInterstitialPageController.onSubmit(
          FakeRequest()
            .withFormUrlEncodedBody()
            .withMethod("POST")
        )

        status(result) mustBe BAD_REQUEST
        contentAsString(result) must include("Select how you want to spread the cost of your Self Assessment")

      }
    }

    "saBppInterstitialPageController.onSubmit called with form parameters - saBppAdvancePayment and pta origin" must {
      "return SA BPP redirect url for advance payment pta-origin" in {

        val answer: (String, String) = "saBppWhatPaymentType" -> "saBppAdvancePayment"

        val result: Future[Result] = TestSaBppInterstitialPageController.onSubmit(
          FakeRequest()
            .withFormUrlEncodedBody(answer)
            .withMethod("POST")
        )

        status(result) mustBe SEE_OTHER
        redirectLocation(
          result
        ).get mustBe "https://www.gov.uk/pay-self-assessment-tax-bill/pay-weekly-monthly?calledFrom=pta-sa&lang=en"

      }
    }

    "saBppInterstitialPageController.onSubmit called with form parameters - saBppOverduePayment with pta origin" must {
      "return SA BPP redirect url for overdue payment with origin as pta-sa" in {

        val answer: (String, String) = "saBppWhatPaymentType" -> "saBppOverduePayment"

        val result: Future[Result] = TestSaBppInterstitialPageController.onSubmit(
          FakeRequest()
            .withFormUrlEncodedBody(answer)
            .withMethod("POST")
        )

        status(result) mustBe SEE_OTHER
        redirectLocation(
          result
        ).get mustBe "http://localhost:9063/pay-what-you-owe-in-instalments?calledFrom=pta-sa"

      }
    }
  }

}
