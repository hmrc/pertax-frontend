/*
 * Copyright 2020 HM Revenue & Customs
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

import config.ConfigDecorator
import controllers.bindable.SoleAddrType
import models.dto.AddressPageVisitedDto
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.EditAddressLockRepository
import uk.gov.hmrc.http.cache.client.CacheMap
import views.html.personaldetails.{AddressAlreadyUpdatedView, InternationalAddressChoiceView}

class InternationalAddressChoiceControllerSpec extends AddressBaseSpec {

  trait LocalSetup extends AddressControllerSetup {

    def controller: InternationalAddressChoiceController =
      new InternationalAddressChoiceController(
        addressJourneyCachingHelper,
        mockAuthJourney,
        withActiveTabAction,
        cc,
        injected[InternationalAddressChoiceView],
        displayAddressInterstitialView,
        injected[EditAddressLockRepository],
        injected[AddressAlreadyUpdatedView]
      )

    def sessionCacheResponse: Option[CacheMap] =
      Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]
  }

  "onPageLoad" should {

    "return OK if there is an entry in the cache to say the user previously visited the 'personal details' page" in new LocalSetup {

      val result = controller.onPageLoad(SoleAddrType)(currentRequest)

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect back to the start of the journey if there is no entry in the cache to say the user previously visited the 'personal details' page" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] = None

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }
  }

  "onSubmit" should {

    "redirect to postcode lookup page when supplied with value = Yes (true)" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("internationalAddressChoice" -> "true")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/find-address")
    }

    "redirect to 'cannot use this service' when service configured to prevent updating International Addresses" in new LocalSetup {

      lazy val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

      when(mockConfigDecorator.updateInternationalAddressInPta).thenReturn(false)

      override def controller: InternationalAddressChoiceController =
        new InternationalAddressChoiceController(
          addressJourneyCachingHelper,
          mockAuthJourney,
          withActiveTabAction,
          cc,
          injected[InternationalAddressChoiceView],
          displayAddressInterstitialView,
          injected[EditAddressLockRepository],
          injected[AddressAlreadyUpdatedView]
        )(partialRetriever, mockConfigDecorator, templateRenderer, ec)

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("internationalAddressChoice" -> "false")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/cannot-use-the-service")
    }

    "return a bad request when supplied no value" in new LocalSetup {

      override def currentRequest[A]: Request[A] = FakeRequest("POST", "").asInstanceOf[Request[A]]

      val result = controller.onSubmit(SoleAddrType)(currentRequest)

      status(result) shouldBe BAD_REQUEST
    }
  }
}
