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

package controllers.address

import controllers.address
import controllers.bindable.{PostalAddrType, ResidentialAddrType}
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.addresslookup.{Address, AddressRecord, Country, RecordSet}
import models.dto.{AddressPageVisitedDto, DateDto, Dto}
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{times, verify}
import play.api.http.Status.{BAD_REQUEST, OK, SEE_OTHER}
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.AddressSelectorService
import testUtils.Fixtures.{oneAndTwoOtherPlacePafRecordSet, oneOtherPlacePafAddressRecord}
import uk.gov.hmrc.http.cache.client.CacheMap
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.AddressSelectorView

import java.time.LocalDate

class AddressSelectorControllerSpec extends AddressBaseSpec {

  trait LocalSetup extends AddressControllerSetup {

    def controller: AddressSelectorController =
      new AddressSelectorController(
        new AddressJourneyCachingHelper(mockLocalSessionCache),
        mockLocalSessionCache,
        mockAuthJourney,
        cc,
        errorRenderer,
        injected[AddressSelectorView],
        injected[DisplayAddressInterstitialView],
        injected[AddressSelectorService]
      )

    def sessionCacheResponse: Option[CacheMap] =
      Some(
        CacheMap(
          "id",
          Map(
            "addressLookupServiceDown"     -> Json.toJson(Some(false)),
            "residentialSelectedRecordSet" -> Json.toJson(oneAndTwoOtherPlacePafRecordSet)
          )
        )
      )
  }

  "onPageLoad" should {

    "render the page with the results of the address lookup" when {
      "RecordSet can be retrieved from the cache" in new LocalSetup {
        override def currentRequest[A]: Request[A] =
          FakeRequest("POST", "")
            .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
            .asInstanceOf[Request[A]]

        override def fetchAndGetEntryDto: Option[Dto] = Some(
          RecordSet(Seq(AddressRecord("id", Address(List("line"), None, None, "AA1 1AA", None, Country.UK), "en")))
        )

        val result = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

        status(result) mustBe OK
        verify(mockLocalSessionCache, times(1)).fetchAndGetEntry[AddressPageVisitedDto](any())(any(), any(), any())
      }
    }

    "redirect to postcode lookup form" when {
      "RecordSet can not be retrieved from the cache" in new LocalSetup {
        override def currentRequest[A]: Request[A] =
          FakeRequest("POST", "")
            .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
            .asInstanceOf[Request[A]]

        override def sessionCacheResponse: Some[CacheMap] = Some(CacheMap("id", Map.empty))

        val result = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(
          address.routes.PostcodeLookupController.onPageLoad(ResidentialAddrType).url
        )
        verify(mockLocalSessionCache, times(1)).fetchAndGetEntry[AddressPageVisitedDto](any())(any(), any(), any())
      }
    }
  }

  "onSubmit" should {

    "call the address lookup service and return 400" when {
      "supplied no addressId in the form" in new LocalSetup {
        override def currentRequest[A]: Request[A] =
          FakeRequest("POST", "")
            .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
            .asInstanceOf[Request[A]]

        override def sessionCacheResponse: Option[CacheMap] =
          Some(
            CacheMap(
              "id",
              Map(
                "addressLookupServiceDown" -> Json.toJson(Some(false)),
                "postalSelectedRecordSet"  -> Json.toJson(oneAndTwoOtherPlacePafRecordSet)
              )
            )
          )

        val result = controller.onSubmit(PostalAddrType)(FakeRequest())

        status(result) mustBe BAD_REQUEST
        verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      }

      "supplied no addressId in the form with a filter" in new LocalSetup {

        override def currentRequest[A]: Request[A] =
          FakeRequest("POST", "")
            .withFormUrlEncodedBody("postcode" -> "AA1 1AA", "filter" -> "7")
            .asInstanceOf[Request[A]]

        override def sessionCacheResponse: Option[CacheMap] =
          Some(
            CacheMap(
              "id",
              Map(
                "addressLookupServiceDown" -> Json.toJson(Some(false)),
                "postalSelectedRecordSet"  -> Json.toJson(oneAndTwoOtherPlacePafRecordSet)
              )
            )
          )

        val result = controller.onSubmit(PostalAddrType)(FakeRequest())

        status(result) mustBe BAD_REQUEST

        verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      }
    }

    "call the address lookup service and redirect to the edit address form for a postal address type when supplied with an addressId" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("addressId" -> "GB990091234514", "postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "addressLookupServiceDown" -> Json.toJson(Some(false)),
              "postalSelectedRecordSet"  -> Json.toJson(oneAndTwoOtherPlacePafRecordSet)
            )
          )
        )

      val result = controller.onSubmit(PostalAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/edit-address")
      verify(mockLocalSessionCache, times(1))
        .cache(meq("postalSelectedAddressRecord"), meq(oneOtherPlacePafAddressRecord))(any(), any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "call the address lookup service and return a 500 when an invalid addressId is supplied in the form" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("addressId" -> "GB000000000000", "postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PostalAddrType)(FakeRequest())

      status(result) mustBe INTERNAL_SERVER_ERROR
      verify(mockLocalSessionCache, times(0)).cache(any(), any())(any(), any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect to enter start date page if postcode is different to currently held postcode" in new LocalSetup {
      override def sessionCacheResponse =
        Some(
          CacheMap(
            "id",
            Map(
              "addressLookupServiceDown"     -> Json.toJson(Some(false)),
              "residentialSelectedRecordSet" -> Json.toJson(oneAndTwoOtherPlacePafRecordSet)
            )
          )
        )

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("addressId" -> "GB990091234515", "postcode" -> "AA1 2AA")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/enter-start-date")
    }

    "redirect to check and submit page if postcode is not different to currently held postcode" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "addressLookupServiceDown"     -> Json.toJson(Some(false)),
              "residentialSelectedRecordSet" -> Json.toJson(oneAndTwoOtherPlacePafRecordSet)
            )
          )
        )

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("addressId" -> "GB990091234515", "postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
      verify(mockLocalSessionCache, times(1))
        .cache(meq("residentialSubmittedStartDateDto"), meq(DateDto(LocalDate.now())))(any(), any(), any())
    }
  }
}
