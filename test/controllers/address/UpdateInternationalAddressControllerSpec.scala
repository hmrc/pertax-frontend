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

import controllers.bindable.{PostalAddrType, ResidentialAddrType}
import controllers.controllershelpers.CountryHelper
import models.dto.{AddressPageVisitedDto, DateDto, ResidencyChoiceDto}
import java.time.LocalDate
import org.jsoup.Jsoup
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{times, verify}
import play.api.http.Status.{BAD_REQUEST, OK, SEE_OTHER}
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.model.DataEvent
import testUtils.Fixtures.{asInternationalAddressDto, fakeStreetPafAddressRecord, fakeStreetTupleListAddressForUnmodified, fakeStreetTupleListInternationalAddress}
import views.html.personaldetails.UpdateInternationalAddressView

class UpdateInternationalAddressControllerSpec extends AddressBaseSpec {

  trait LocalSetup extends AddressControllerSetup {

    def controller: UpdateInternationalAddressController =
      new UpdateInternationalAddressController(
        injected[CountryHelper],
        addressJourneyCachingHelper,
        mockAuditConnector,
        mockAuthJourney,
        cc,
        injected[UpdateInternationalAddressView],
        displayAddressInterstitialView
      )

    def sessionCacheResponse: Option[CacheMap] =
      Some(CacheMap("id", Map("residentialSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord))))

    def currentRequest[A]: Request[A]          = FakeRequest().asInstanceOf[Request[A]]
  }

  "onPageLoad" must {

    "find only the selected address from the session cache and no residency choice and return 303" in new LocalSetup {

      val result = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "fetch the selected address and a residential residencyChoice has been selected from the session cache and return 200" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "selectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
              "addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true))
            )
          )
        )

      val result = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find no selected address with residential address type but residencyChoice in the session cache and still return 200" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result                                          = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find no residency choice in the session cache and redirect to the beginning of the journey" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] = Some(CacheMap("id", Map.empty))

      val result = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
    }

    "redirect user to beginning of journey and return 303 for postal addressType and no pagevisitedDto in cache" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] = None

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
    }

    "display edit address page and return 200 for postal addressType with pagevisitedDto and addressRecord in cache" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)),
              "selectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord)
            )
          )
        )

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "display edit address page and return 200 for postal addressType with pagevisitedDto and no addressRecord in cache" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result                                          = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find no addresses in the session cache and return 303" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] = Some(CacheMap("id", Map.empty))

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find residential selected and submitted addresses in the session cache and return 200" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
              "residentialSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
              "addressPageVisitedDto"            -> Json.toJson(AddressPageVisitedDto(true))
            )
          )
        )

      val result = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find no selected address but a submitted address in the session cache and return 200" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
              "addressPageVisitedDto"          -> Json.toJson(AddressPageVisitedDto(true))
            )
          )
        )

      val result = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "show 'Enter the address' when user amends correspondence address manually and address has not been selected" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result                                          = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      val doc = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("govuk-fieldset__heading").toString().contains("Your postal address") mustBe true
    }

    "show 'Enter your address' when user amends residential address manually and address has not been selected" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result                                          = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      val doc = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("govuk-fieldset__heading").toString().contains("Your address") mustBe true
    }

    "verify an audit event has been sent when user chooses to add/amend view address" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result                                          = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
    }

    "verify an audit event has been sent when user chooses to add postal address" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result                                          = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
    }
  }

  "onSubmit" must {

    "return 400 when supplied invalid form input" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("selectedAddressRecord" -> Json.toJson(""))))

      val result                                          = controller.onSubmit(PostalAddrType)(FakeRequest("POST", ""))

      status(result) mustBe BAD_REQUEST
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return 303, caching addressDto and redirecting to review changes page when supplied valid form input on a postal journey and input default startDate into cache" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressLookupServiceDown" -> Json.toJson(Some(true)))))

      override def currentRequest[A]: Request[A]          =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody(fakeStreetTupleListInternationalAddress: _*)
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PostalAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/changes")
      verify(mockLocalSessionCache, times(1)).cache(
        meq("postalSubmittedAddressDto"),
        meq(asInternationalAddressDto(fakeStreetTupleListInternationalAddress))
      )(any(), any(), any())
      verify(mockLocalSessionCache, times(1))
        .cache(meq("postalSubmittedStartDateDto"), meq(DateDto(LocalDate.now())))(any(), any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return 303, caching addressDto and redirecting to enter start date page when supplied valid form input on a non postal journey" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressLookupServiceDown" -> Json.toJson(Some(true)))))

      override def currentRequest[A]: Request[A]          =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody(fakeStreetTupleListInternationalAddress: _*)
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/enter-start-date")
      verify(mockLocalSessionCache, times(1)).cache(
        meq("residentialSubmittedAddressDto"),
        meq(asInternationalAddressDto(fakeStreetTupleListInternationalAddress))
      )(any(), any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }
  }
}
