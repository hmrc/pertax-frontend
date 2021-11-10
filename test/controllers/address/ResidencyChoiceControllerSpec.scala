/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.address

import models.dto.TaxCreditsChoiceDto
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify}
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.cache.client.CacheMap
import views.html.personaldetails.ResidencyChoiceView

class ResidencyChoiceControllerSpec extends AddressBaseSpec {

  trait LocalSetup extends AddressControllerSetup {

    def controller: ResidencyChoiceController =
      new ResidencyChoiceController(
        addressJourneyCachingHelper,
        mockAuthJourney,
        withActiveTabAction,
        cc,
        injected[ResidencyChoiceView],
        displayAddressInterstitialView
      )

    def sessionCacheResponse: Option[CacheMap] =
      Some(CacheMap("id", Map("taxCreditsChoiceDto" -> Json.toJson(TaxCreditsChoiceDto(false)))))

    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]
  }

  "onPageLoad" must {

    "return OK when the user has indicated that they do not receive tax credits on the previous page" in new LocalSetup {

      val result = controller.onPageLoad(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return to the beginning of journey when the user has indicated that they receive tax credits on the previous page" in new LocalSetup {
      override def sessionCacheResponse: Some[CacheMap] =
        Some(CacheMap("id", Map("taxCreditsChoiceDto" -> Json.toJson(TaxCreditsChoiceDto(true)))))

      val result = controller.onPageLoad(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-profile")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return to the beginning of journey when the user has not selected any tax credits choice on the previous page" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] = None

      val result = controller.onPageLoad(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-profile")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }
  }

  "onSubmit" must {

    "redirect to find address page with primary type when supplied value=primary" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("residencyChoice" -> "primary")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/primary/do-you-live-in-the-uk")
    }

    "redirect to find address page with sole type when supplied value=sole" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("residencyChoice" -> "sole")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/sole/do-you-live-in-the-uk")
    }

    "return a bad request when supplied value=bad" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("residencyChoice" -> "bad")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(FakeRequest())

      status(result) mustBe BAD_REQUEST
    }

    "return a bad request when supplied no value" in new LocalSetup {

      override def currentRequest[A]: Request[A] = FakeRequest("POST", "").asInstanceOf[Request[A]]

      val result = controller.onSubmit(FakeRequest())

      status(result) mustBe BAD_REQUEST
    }
  }
}
