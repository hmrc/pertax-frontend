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

import controllers.bindable.{PostalAddrType, ResidentialAddrType}
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.{Address, ETag}
import models.dto.{DateDto, InternationalAddressChoiceDto}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => meq}
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.i18n.{Lang, Messages}
import play.api.libs.json.Json
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testUtils.Fixtures
import testUtils.Fixtures._
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.model.DataEvent
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.{ReviewChangesView, UpdateAddressConfirmationView}

import java.time.LocalDate
import scala.concurrent.Future

class AddressSubmissionControllerSpec extends AddressBaseSpec {

  implicit lazy val lang: Lang = Lang("en")

  trait LocalSetup extends AddressControllerSetup {

    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

    def controller: AddressSubmissionController =
      new AddressSubmissionController(
        mockCitizenDetailsService,
        mockAddressMovedService,
        mockEditAddressLockRepository,
        mockAuthJourney,
        new AddressJourneyCachingHelper(mockLocalSessionCache),
        mockAuditConnector,
        inject[MessagesControllerComponents],
        errorRenderer,
        inject[UpdateAddressConfirmationView],
        inject[ReviewChangesView],
        inject[DisplayAddressInterstitialView],
        mockFeatureFlagService,
        internalServerErrorView
      )(config, ec)
  }

  "onPageLoad" must {

    "return 200 if only SubmittedAddressDto is present in keystore for postal" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "postalSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )
          )
        )

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect back to start of journey if SubmittedAddressDto is missing from keystore for non-postal" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )
          )
        )

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect back to start of journey if SubmittedAddressDto is missing from keystore for postal" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] = Some(
        CacheMap(
          "id",
          Map()
        )
      )

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }
    "display the appropriate label for address when the residential address has changed" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSubmittedAddressDto"   -> Json.toJson(
                asAddressDto(fakeStreetTupleListAddressForModifiedPostcode)
              ),
              "residentialSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          )
        )

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      contentAsString(result) must include(Messages("label.your_new_address"))
      contentAsString(result) must include(Messages("label.when_you_started_living_here"))
    }

    "display the appropriate label for address when the residential address has not changed" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
              "residentialSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          )
        )

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

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
    ): DataEvent = DataEvent(
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
        "originalLine1"     -> Some("1 Fake Street").filter(_ => includeOriginals),
        "originalLine2"     -> Some("Fake Town").filter(_ => includeOriginals),
        "originalLine3"     -> Some("Fake City").filter(_ => includeOriginals),
        "originalLine4"     -> Some("Fake Region").filter(_ => includeOriginals),
        "originalPostcode"  -> Some("AA1 1AA").filter(_ => includeOriginals),
        "originalCountry"   -> Some("Country(UK,United Kingdom)").filter(_ => includeOriginals),
        "originalUPRN"      -> uprn.filter(_ => includeOriginals)
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

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(controller.editAddressLockRepository, times(0))
        .insert(meq(nino.withoutSuffix), meq(ResidentialAddrType))

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

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "render the thank-you page if postalSubmittedStartDateDto is not in the cache, and the journey type is PostalAddrType" in new LocalSetup {
      override lazy val fakeAddress: Address              =
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

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any(), any())
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

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")

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

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      val arg       = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) mustBe comparatorDataEvent(
        dataEvent,
        "postcodeAddressSubmitted",
        Some("GB101"),
        includeOriginals = false
      )
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any(), any())
    }

    "render the thank you page and log a postcodeAddressSubmitted audit event upon successful submission of an unmodified address, this time using postal type and having no postalSubmittedStartDateDto in the cache " in new LocalSetup {
      override lazy val fakeAddress: Address              =
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

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      val arg       = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) mustBe comparatorDataEvent(
        dataEvent,
        "postcodeAddressSubmitted",
        Some("GB101"),
        includeOriginals = false,
        addressType = Some("Correspondence")
      )
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any(), any())
    }

    "render the thank you page and log a manualAddressSubmitted audit event upon successful submission of a manually entered address" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSubmittedAddressDto"   -> Json.toJson(
                asAddressDto(fakeStreetTupleListAddressForManuallyEntered)
              ),
              "residentialSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          )
        )

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      val arg       = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) mustBe comparatorDataEvent(
        dataEvent,
        "manualAddressSubmitted",
        None,
        includeOriginals = false
      )
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any(), any())
    }

    "render the thank you page and log a postcodeAddressModifiedSubmitted audit event upon successful of a modified address" in new LocalSetup {
      override lazy val fakeAddress: Address              = buildFakeAddress.copy(line1 = Some("11 Fake Street"), isRls = false)
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

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      val arg       = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) mustBe comparatorDataEvent(
        dataEvent,
        "postcodeAddressModifiedSubmitted",
        Some("GB101"),
        includeOriginals = true,
        Some("11 Fake Street")
      )
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any(), any())
    }

    "return 500 when fetching etag from citizen details fails" in new LocalSetup {

      override def eTagResponse: Option[ETag] = None

      override lazy val fakeAddress: Address              =
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

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(FakeRequest())

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "render the confirmation page with the P85 messaging when updating to move to international address" in new LocalSetup {
      override lazy val fakeAddress: Address              = buildFakeAddress.copy(line1 = Some("11 Fake Street"), isRls = false)
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecordOutsideUk),
              "residentialSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForModified)),
              "residentialSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015)),
              "internationalAddressChoiceDto"    -> Json.toJson(InternationalAddressChoiceDto(false))
            )
          )
        )

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(FakeRequest())

      status(result) mustBe 200
      contentAsString(result) must include("Complete a P85 form (opens in new tab)")

    }
    "render the confirmation page without the P85 messaging when updating a UK address" in new LocalSetup {
      override lazy val fakeAddress: Address              = buildFakeAddress.copy(line1 = Some("11 Fake Street"), isRls = false)
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "residentialSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecordOutsideUk),
              "residentialSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForModified)),
              "residentialSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015)),
              "internationalAddressChoiceDto"    -> Json.toJson(InternationalAddressChoiceDto(true))
            )
          )
        )

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(FakeRequest())

      status(result) mustBe 200
      contentAsString(result) mustNot include("Complete a P85 form (opens in new tab)")

    }

  }
}
