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

import controllers.controllershelpers.PersonalDetailsCardGenerator
import models.dto.AddressPageVisitedDto
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{times, verify, when}
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation, status}
import services.NinoDisplayService
import uk.gov.hmrc.http.cache.client.CacheMap
import util.Fixtures
import viewmodels.PersonalDetailsViewModel
import views.html.personaldetails.PersonalDetailsView

import scala.concurrent.Future

class PersonalDetailsControllerSpec extends AddressBaseSpec {

  val ninoDisplayService = mock[NinoDisplayService]

  trait LocalSetup extends AddressControllerSetup {

    when(ninoDisplayService.getNino(any(), any())).thenReturn {
      Future.successful(Some(Fixtures.fakeNino))
    }

    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

    def controller =
      new PersonalDetailsController(
        injected[PersonalDetailsCardGenerator],
        injected[PersonalDetailsViewModel],
        mockEditAddressLockRepository,
        ninoDisplayService,
        mockAuthJourney,
        addressJourneyCachingHelper,
        withActiveTabAction,
        mockAuditConnector,
        cc,
        displayAddressInterstitialView,
        injected[PersonalDetailsView]
      )
  }

  "Calling AddressController.redirectToYourProfile" must {
    "redirect to the your-profile page" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] = None

      val result = controller.redirectToYourProfile()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-profile")
    }
  }

  "Calling AddressController.onPageLoad" must {
    "call citizenDetailsService.fakePersonDetails and return 200" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "addressPageVisitedDto" -> Json
                .toJson(AddressPageVisitedDto(true))
            )
          )
        )

      val result = controller.onPageLoad()(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1))
        .cache(
          meq("addressPageVisitedDto"),
          meq(AddressPageVisitedDto(true))
        )(any(), any(), any())
      verify(mockEditAddressLockRepository, times(1)).get(any())
    }
  }
}
