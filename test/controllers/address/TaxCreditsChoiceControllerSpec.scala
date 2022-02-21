/*
 * Copyright 2022 HM Revenue & Customs
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

import models.dto.AddressPageVisitedDto
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import play.api.http.Status.SEE_OTHER
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.TaxCreditsService
import uk.gov.hmrc.http.cache.client.CacheMap
import views.html.InternalServerErrorView

import scala.concurrent.Future

class TaxCreditsChoiceControllerSpec extends AddressBaseSpec {

  trait LocalSetup extends AddressControllerSetup {

    lazy val taxCreditsService: TaxCreditsService = mock[TaxCreditsService]

    def controller: TaxCreditsChoiceController =
      new TaxCreditsChoiceController(
        mockAuthJourney,
        withActiveTabAction,
        cc,
        addressJourneyCachingHelper,
        displayAddressInterstitialView,
        taxCreditsService,
        injected[InternalServerErrorView]
      )

    def sessionCacheResponse: Option[CacheMap] =
      Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]
  }

  "onPageLoad" must {

    "return SEE_OTHER and the correct redirect if the user has tax credits" in new LocalSetup {
      when(taxCreditsService.checkForTaxCredits(any())(any())).thenReturn(Future.successful(Some(true)))

      val result = controller.onPageLoad(currentRequest)

      status(result) mustBe SEE_OTHER

      redirectLocation(result) mustBe Some("http://localhost:9362/tax-credits-service/personal/change-address")

      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return SEE_OTHER and the correct redirect if the user hasn't got tax credits" in new LocalSetup {
      when(taxCreditsService.checkForTaxCredits(any())(any())).thenReturn(Future.successful(Some(false)))

      val result = controller.onPageLoad(currentRequest)

      status(result) mustBe SEE_OTHER

      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/do-you-live-in-the-uk")

      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return INTERNAL_SERVER_ERROR and no redirect URL if the service returns None" in new LocalSetup {
      when(taxCreditsService.checkForTaxCredits(any())(any())).thenReturn(Future.successful(None))

      val result = controller.onPageLoad(currentRequest)

      status(result) mustBe INTERNAL_SERVER_ERROR

      redirectLocation(result) mustBe None

      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect back to the start of the journey if there is no entry in the cache to say the user previously visited the 'personal details' page" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] = None

      val result = controller.onPageLoad(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-profile")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }
  }
}
