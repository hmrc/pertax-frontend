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

import controllers.auth.requests.UserRequest
import controllers.bindable.{PostalAddrType, PrimaryAddrType, SoleAddrType}
import models.PersonDetails
import models.dto.{AddressPageVisitedDto, DateDto}
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{times, verify, when}
import play.api.libs.json.Json
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.language.LanguageUtils
import util.ActionBuilderFixture
import util.Fixtures.fakeStreetTupleListAddressForUnmodified
import util.UserRequestFixture.buildUserRequest
import util.fixtures.AddressFixture.{address => addressFixture}
import util.fixtures.PersonFixture.emptyPerson
import views.html.personaldetails.{CannotUpdateAddressView, EnterStartDateView}

import scala.concurrent.Future

class StartDateControllerSpec extends AddressBaseSpec {

  trait LocalSetup extends AddressControllerSetup {

    def controller: StartDateController =
      new StartDateController(
        mockAuthJourney,
        withActiveTabAction,
        cc,
        addressJourneyCachingHelper,
        injected[LanguageUtils],
        injected[EnterStartDateView],
        injected[CannotUpdateAddressView],
        displayAddressInterstitialView
      )

    def sessionCacheResponse: Option[CacheMap] =
      Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]
  }

  "onPageLoad" must {

    "return 200 when passed PrimaryAddrType and submittedAddressDto is in keystore" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] = Some(
        CacheMap(
          "id",
          Map("primarySubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)))
        )
      )

      val result = controller.onPageLoad(PrimaryAddrType)(currentRequest)

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return 200 when passed SoleAddrType and submittedAddressDto is in keystore" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] = Some(
        CacheMap(
          "id",
          Map("soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)))
        )
      )

      val result = controller.onPageLoad(SoleAddrType)(currentRequest)

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect to 'edit address' when passed PostalAddrType as this step is not valid for postal" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.onPageLoad(PostalAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/edit-address")
      verify(mockLocalSessionCache, times(0)).fetch()(any(), any())
    }

    "redirect back to start of journey if submittedAddressDto is missing from keystore" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] = Some(CacheMap("id", Map.empty))

      val result = controller.onPageLoad(PrimaryAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-profile")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }
  }

  "onSubmit" must {

    "return 303 when passed PrimaryAddrType and a valid form with low numbers" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "1", "startDate.month" -> "1", "startDate.year" -> "2016")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PrimaryAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/primary/changes")
      verify(mockLocalSessionCache, times(1))
        .cache(meq("primarySubmittedStartDateDto"), meq(DateDto.build(1, 1, 2016)))(any(), any(), any())
    }

    "return 303 when passed PrimaryAddrType and date is in the today" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "2", "startDate.month" -> "2", "startDate.year" -> "2016")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PrimaryAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/primary/changes")
      verify(mockLocalSessionCache, times(1))
        .cache(meq("primarySubmittedStartDateDto"), meq(DateDto.build(2, 2, 2016)))(any(), any(), any())
    }

    "redirect to the changes to sole address page when passed PrimaryAddrType and a valid form with high numbers" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "31", "startDate.month" -> "12", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(SoleAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/sole/changes")
      verify(mockLocalSessionCache, times(1))
        .cache(meq("soleSubmittedStartDateDto"), meq(DateDto.build(31, 12, 2019)))(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and missing date fields" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "").withFormUrlEncodedBody().asInstanceOf[Request[A]]

      val result =
        controller.onSubmit(PrimaryAddrType)(currentRequest)
      status(result) mustBe BAD_REQUEST
      verify(mockLocalSessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and day out of range - too early" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "0", "startDate.month" -> "1", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(PrimaryAddrType)(currentRequest)
      status(result) mustBe BAD_REQUEST
      verify(mockLocalSessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and day out of range - too late" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "32", "startDate.month" -> "1", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(PrimaryAddrType)(currentRequest)

      status(result) mustBe BAD_REQUEST
      verify(mockLocalSessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and month out of range at lower bound" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "1", "startDate.month" -> "0", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(PrimaryAddrType)(currentRequest)
      status(result) mustBe BAD_REQUEST
      verify(mockLocalSessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and month out of range at upper bound" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "31", "startDate.month" -> "13", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(PrimaryAddrType)(currentRequest)
      status(result) mustBe BAD_REQUEST
      verify(mockLocalSessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and the updated start date is not after the start date on record" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "3", "startDate.month" -> "2", "startDate.year" -> "2016")
          .asInstanceOf[Request[A]]

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = currentRequest,
              personDetails = Some(
                PersonDetails(emptyPerson, Some(addressFixture(startDate = Some(new LocalDate(2016, 11, 22)))), None)
              )
            ).asInstanceOf[UserRequest[A]]
          )
      })

      val result: Future[Result] = controller.onSubmit(PrimaryAddrType)(currentRequest)
      status(result) mustBe BAD_REQUEST
      verify(mockLocalSessionCache, times(1)).cache(any(), any())(any(), any(), any())
    }

    "return a 400 when startDate is earlier than recorded with sole address type" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "14", "startDate.month" -> "03", "startDate.year" -> "2015")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(SoleAddrType)(currentRequest)

      status(result) mustBe BAD_REQUEST
    }

    "return a 400 when startDate is the same as recorded with sole address type" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "15", "startDate.month" -> "03", "startDate.year" -> "2015")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(SoleAddrType)(currentRequest)

      status(result) mustBe BAD_REQUEST
    }

    "return a 400 when startDate is earlier than recorded with primary address type" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "14", "startDate.month" -> "03", "startDate.year" -> "2015")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PrimaryAddrType)(currentRequest)

      status(result) mustBe BAD_REQUEST
    }

    "return a 400 when startDate is the same as recorded with primary address type" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "15", "startDate.month" -> "03", "startDate.year" -> "2015")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PrimaryAddrType)(currentRequest)

      status(result) mustBe BAD_REQUEST
    }

    "redirect to correct successful url when supplied with startDate after recorded with sole address type" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "16", "startDate.month" -> "03", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(SoleAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/sole/changes")
    }

    "redirect to correct successful url when supplied with startDate after startDate on record with primary address" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PrimaryAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/primary/changes")
    }

    "redirect to success page when no startDate is on record" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PrimaryAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/primary/changes")
    }

    "redirect to success page when no address is on record" in new LocalSetup {

      override def sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PrimaryAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/primary/changes")
    }
  }
}
