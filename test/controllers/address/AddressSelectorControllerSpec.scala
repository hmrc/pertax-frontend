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
import models.UserAnswers
import models.addresslookup.{Address, AddressRecord, Country, RecordSet}
import org.mockito.ArgumentMatchers.any
import play.api.http.Status.{BAD_REQUEST, OK, SEE_OTHER}
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import routePages.{AddressLookupServiceDownPage, SelectedRecordSetPage}
import testUtils.Fixtures.oneAndTwoOtherPlacePafRecordSet
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class AddressSelectorControllerSpec extends AddressBaseSpec {
  private def controller: AddressSelectorController = app.injector.instanceOf[AddressSelectorController]

  "onPageLoad" should {

    "render the page with the results of the address lookup" when {
      "RecordSet can be retrieved from the cache" in {
        def currentRequest[A]: Request[A] =
          FakeRequest("POST", "")
            .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
            .asInstanceOf[Request[A]]

        val recordSet: RecordSet =
          RecordSet(Seq(AddressRecord("id", Address(List("line"), None, None, "AA1 1AA", None, Country.UK), "en")))

        val userAnswers: UserAnswers = UserAnswers
          .empty("id")
          .setOrException(AddressLookupServiceDownPage, false)
          .setOrException(SelectedRecordSetPage(ResidentialAddrType), recordSet)

        when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

        val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(currentRequest)

        status(result) mustBe OK
        verify(mockJourneyCacheRepository, times(1)).get(any())
      }
    }

    "redirect to postcode lookup form" when {
      "RecordSet can not be retrieved from the cache" in {
        def currentRequest[A]: Request[A] =
          FakeRequest("POST", "")
            .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
            .asInstanceOf[Request[A]]

        val userAnswers: UserAnswers = UserAnswers.empty("id")
        when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

        val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(currentRequest)

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(
          address.routes.PostcodeLookupController.onPageLoad(ResidentialAddrType).url
        )
        verify(mockJourneyCacheRepository, times(1)).get(any())
      }
    }
  }

  "onSubmit" should {

    "call the address lookup service and return 400" when {
      "supplied no addressId in the form" in {
        val userAnswers: UserAnswers = UserAnswers
          .empty("id")
          .setOrException(AddressLookupServiceDownPage, false)
          .setOrException(SelectedRecordSetPage(PostalAddrType), oneAndTwoOtherPlacePafRecordSet)

        when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
        when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

        val result: Future[Result] = controller.onSubmit(PostalAddrType)(FakeRequest())

        status(result) mustBe BAD_REQUEST
        verify(mockJourneyCacheRepository, times(1)).get(any())
      }

      "supplied no addressId in the form with a filter" in {
        val userAnswers: UserAnswers = UserAnswers
          .empty("id")
          .setOrException(AddressLookupServiceDownPage, false)
          .setOrException(SelectedRecordSetPage(PostalAddrType), oneAndTwoOtherPlacePafRecordSet)

        when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
        when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

        val result: Future[Result] = controller.onSubmit(PostalAddrType)(FakeRequest())

        status(result) mustBe BAD_REQUEST

        verify(mockJourneyCacheRepository, times(1)).get(any())
      }
    }

    "call the address lookup service and redirect to the edit address form for a postal address type when supplied with an addressId" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("addressId" -> "GB990091234514", "postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(AddressLookupServiceDownPage, false)
        .setOrException(SelectedRecordSetPage(PostalAddrType), oneAndTwoOtherPlacePafRecordSet)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/edit-address")
    }

    "call the address lookup service and return a 500 when an invalid addressId is supplied in the form" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(AddressLookupServiceDownPage, false)
        .setOrException(SelectedRecordSetPage(ResidentialAddrType), oneAndTwoOtherPlacePafRecordSet)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(FakeRequest())

      status(result) mustBe INTERNAL_SERVER_ERROR
      verify(mockJourneyCacheRepository, times(0)).set(any[UserAnswers])
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "redirect to enter start date page if postcode is different to currently held postcode" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("addressId" -> "GB990091234515", "postcode" -> "AA1 2AA")
          .asInstanceOf[Request[A]]

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(AddressLookupServiceDownPage, false)
        .setOrException(SelectedRecordSetPage(ResidentialAddrType), oneAndTwoOtherPlacePafRecordSet)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/enter-start-date")
    }

    "redirect to check and submit page if postcode is not different to currently held postcode" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(AddressLookupServiceDownPage, false)
        .setOrException(SelectedRecordSetPage(ResidentialAddrType), oneAndTwoOtherPlacePafRecordSet)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("addressId" -> "GB990091234515", "postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }
  }
}
