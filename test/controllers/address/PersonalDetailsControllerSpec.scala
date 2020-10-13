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

import controllers.controllershelpers.PersonalDetailsCardGenerator
import models.dto.AddressPageVisitedDto
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito.{times, verify, when}
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.test.FakeRequest
import services.NinoDisplayService
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.model.DataEvent
import util.Fixtures
import views.html.personaldetails.{AddressAlreadyUpdatedView, PersonalDetailsView}

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
        mockEditAddressLockRepository,
        ninoDisplayService,
        mockAuthJourney,
        addressJourneyCachingHelper,
        withActiveTabAction,
        mockAuditConnector,
        cc,
        displayAddressInterstitialView,
        injected[PersonalDetailsView],
        injected[AddressAlreadyUpdatedView]
      ) {}

    "Calling AddressController.onPageLoad" should {

      "call citizenDetailsService.fakePersonDetails and return 200" in new LocalSetup {
        override def sessionCacheResponse: Option[CacheMap] =
          Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

        val result = controller.onPageLoad()(FakeRequest())

        status(result) shouldBe OK
        verify(mockLocalSessionCache, times(1))
          .cache(meq("addressPageVisitedDto"), meq(AddressPageVisitedDto(true)))(any(), any(), any())
        verify(mockEditAddressLockRepository, times(1)).get(any())
      }

      "send an audit event when user arrives on personal details page" in new LocalSetup {
        override def sessionCacheResponse: Option[CacheMap] =
          Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

        val result = controller.onPageLoad()(FakeRequest())
        val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])

        status(result) shouldBe OK
        verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      }
    }
  }
}
