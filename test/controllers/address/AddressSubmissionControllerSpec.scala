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

import controllers.auth.WithActiveTabAction
import controllers.bindable.{PostalAddrType, ResidentialAddrType}
import controllers.controllershelpers.AddressJourneyCachingHelper
import error.GenericErrors
import models.ETag
import models.dto.DateDto
import org.joda.time.LocalDate
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{times, verify}
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.i18n.{Lang, Messages}
import play.api.libs.json.Json
import play.api.mvc.{MessagesControllerComponents, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.model.DataEvent
import util.Fixtures
import util.Fixtures._
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.{ReviewChangesView, UpdateAddressConfirmationView}

class AddressSubmissionControllerSpec extends AddressBaseSpec {

  implicit lazy val lang: Lang = Lang("en")

  trait LocalSetup extends AddressControllerSetup {

    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

    def controller =
      new AddressSubmissionController(
        mockCitizenDetailsService,
        mockAddressMovedService,
        mockEditAddressLockRepository,
        mockAuthJourney,
        new AddressJourneyCachingHelper(mockLocalSessionCache),
        injected[WithActiveTabAction],
        mockAuditConnector,
        injected[MessagesControllerComponents],
        errorRenderer,
        injected[UpdateAddressConfirmationView],
        injected[ReviewChangesView],
        injected[DisplayAddressInterstitialView],
        injected[GenericErrors]
      )(config, templateRenderer, ec)
  }

  "onPageLoad" must {

    "return 200 if only SubmittedAddressDto is present in keystore for postal" in new LocalSetup {
      override def sessionCacheResponse =
        Some(
          CacheMap(
            "id",
            Map(
              "postalSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )
          )
        )

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect back to start of journey if SubmittedAddressDto is missing from keystore for non-postal" in new LocalSetup {
      override def sessionCacheResponse =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )
          )
        )

      val result = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-profile")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect back to start of journey if SubmittedAddressDto is missing from keystore for postal" in new LocalSetup {
      override def sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map()
        )
      )

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-profile")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }
    "display the appropriate label for address when the residential address has changed" in new LocalSetup {
      override def sessionCacheResponse =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSubmittedAddressDto" -> Json.toJson(
                asAddressDto(fakeStreetTupleListAddressForModifiedPostcode)
              ),
              "residentialSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          )
        )

      val result = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      contentAsString(result) must include(Messages("label.your_new_address"))
      contentAsString(result) must include(Messages("label.when_you_started_living_here"))
    }

    "display the appropriate label for address when the residential address has not changed" in new LocalSetup {
      override def sessionCacheResponse =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
              "residentialSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          )
        )

      val result = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      contentAsString(result) must include(Messages("label.your_address"))
      contentAsString(result) mustNot include(Messages("label.when_you_started_living_here"))
    }
  }

  "onSubmit" must {

    def comparatorDataEvent(
      dataEvent: DataEvent,
      auditType: String,
      uprn: Option[String],
      includeOriginals: Boolean,
      submittedLine1: Option[String] = Some("1 Fake Street"),
      addressType: Option[String] = Some("Residential")
    ) = DataEvent(
      "pertax-frontend",
      auditType,
      dataEvent.eventId,
      Map("path" -> "/test", "transactionName" -> "change_of_address"),
      Map(
        "nino"              -> Some(Fixtures.fakeNino.nino),
        "etag"              -> Some("115"),
        "submittedLine1"    -> submittedLine1,
        "submittedLine2"    -> Some("Fake Town"),
        "submittedLine3"    -> Some("Fake City"),
        "submittedLine4"    -> Some("Fake Region"),
        "submittedPostcode" -> Some("AA1 1AA"),
        "submittedCountry"  -> None,
        "addressType"       -> addressType,
        "submittedUPRN"     -> uprn,
        "originalLine1"     -> Some("1 Fake Street").filter(x => includeOriginals),
        "originalLine2"     -> Some("Fake Town").filter(x => includeOriginals),
        "originalLine3"     -> Some("Fake City").filter(x => includeOriginals),
        "originalLine4"     -> Some("Fake Region").filter(x => includeOriginals),
        "originalPostcode"  -> Some("AA1 1AA").filter(x => includeOriginals),
        "originalCountry"   -> Some("Country(UK,United Kingdom)").filter(x => includeOriginals),
        "originalUPRN"      -> uprn.filter(x => includeOriginals)
      ).map(t => t._2.map((t._1, _))).flatten.toMap,
      dataEvent.generatedAt
    )

    "redirect to start of journey if ResidentialSubmittedStartDateDto is missing from the cache, and the journey type is ResidentialAddrType" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )
          )
        )

      override def currentRequest[A]: Request[A] = FakeRequest("POST", "/test").asInstanceOf[Request[A]]

      val result = controller.onSubmit(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-profile")

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(ResidentialAddrType))

    }

    "redirect to start of journey if residentialSubmittedStartDateDto is missing from the cache, and the journey type is residentialAddrType" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )
          )
        )

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-profile")

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "render the thank-you page if postalSubmittedStartDateDto is not in the cache, and the journey type is PostalAddrType" in new LocalSetup {
      override lazy val fakeAddress =
        buildFakeAddress.copy(`type` = Some("Correspondence"), startDate = Some(LocalDate.now))
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "postalSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )
          )
        )

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "redirect to start of journey if residentialSubmittedAddressDto is missing from the cache" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          )
        )

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-profile")

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "render the thank-you page and log a postcodeAddressSubmitted audit event upon successful submission of an unmodified address" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
              "residentialSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
              "residentialSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          )
        )

      override def currentRequest[A]: Request[A] = FakeRequest("POST", "/test").asInstanceOf[Request[A]]

      val result = controller.onSubmit(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) mustBe comparatorDataEvent(dataEvent, "postcodeAddressSubmitted", Some("GB101"), false)
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "render the thank you page and log a postcodeAddressSubmitted audit event upon successful submission of an unmodified address, this time using postal type and having no postalSubmittedStartDateDto in the cache " in new LocalSetup {
      override lazy val fakeAddress =
        buildFakeAddress.copy(`type` = Some("Correspondence"), startDate = Some(LocalDate.now))
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "postalSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
              "postalSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )
          )
        )

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody(fakeStreetTupleListAddressForUnmodified: _*)
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) mustBe comparatorDataEvent(
        dataEvent,
        "postcodeAddressSubmitted",
        Some("GB101"),
        false,
        addressType = Some("Correspondence")
      )
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "render the thank you page and log a manualAddressSubmitted audit event upon successful submission of a manually entered address" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSubmittedAddressDto" -> Json.toJson(
                asAddressDto(fakeStreetTupleListAddressForManualyEntered)
              ),
              "residentialSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          )
        )

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) mustBe comparatorDataEvent(dataEvent, "manualAddressSubmitted", None, false)
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "render the thank you page and log a postcodeAddressModifiedSubmitted audit event upon successful of a modified address" in new LocalSetup {
      override lazy val fakeAddress = buildFakeAddress.copy(line1 = Some("11 Fake Street"))
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
              "residentialSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForModified)),
              "residentialSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          )
        )

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) mustBe comparatorDataEvent(
        dataEvent,
        "postcodeAddressModifiedSubmitted",
        Some("GB101"),
        true,
        Some("11 Fake Street")
      )
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "return 500 when fetching etag from citizen details fails" in new LocalSetup {

      override def eTagResponse: Option[ETag] = None

      override lazy val fakeAddress =
        buildFakeAddress.copy(`type` = Some("Correspondence"), startDate = Some(LocalDate.now))
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "postalSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )
          )
        )

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PostalAddrType)(FakeRequest())

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }
}
