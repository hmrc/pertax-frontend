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
import models.UserAnswers
import models.addresslookup.{Address, AddressRecord, Country, RecordSet}
import models.dto.DateDto
import org.mockito.ArgumentMatchers.any
import play.api.http.Status.{BAD_REQUEST, OK, SEE_OTHER}
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import routePages.{AddressLookupServiceDownPage, SelectedRecordSetPage, SubmittedStartDatePage}
import services.AddressSelectorService
import testUtils.Fixtures.{oneAndTwoOtherPlacePafRecordSet, oneOtherPlacePafAddressRecord}
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.AddressSelectorView

import java.time.LocalDate
import scala.concurrent.Future

class AddressSelectorControllerSpec extends AddressBaseSpec {

  trait LocalSetup extends AddressControllerSetup {

    def controller: AddressSelectorController =
      new AddressSelectorController(
        new AddressJourneyCachingHelper(mockJourneyCacheRepository),
        mockJourneyCacheRepository,
        mockAuthJourney,
        cc,
        errorRenderer,
        inject[AddressSelectorView],
        inject[DisplayAddressInterstitialView],
        inject[AddressSelectorService],
        mockFeatureFlagService,
        internalServerErrorView
      )

    val baseUserAnswers: UserAnswers = UserAnswers.empty("id").setOrException(AddressLookupServiceDownPage, false)

    def userAnswersToReturn: UserAnswers = baseUserAnswers
    when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswersToReturn))
    when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
  }

  "onPageLoad" should {

    "render the page with the results of the address lookup" when {
      "RecordSet can be retrieved from the cache" in new LocalSetup {
        override def currentRequest[A]: Request[A] =
          FakeRequest("POST", "")
            .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
            .asInstanceOf[Request[A]]

        val recordSet: RecordSet                      =
          RecordSet(Seq(AddressRecord("id", Address(List("line"), None, None, "AA1 1AA", None, Country.UK), "en")))
        override def userAnswersToReturn: UserAnswers =
          baseUserAnswers
            .setOrException(SelectedRecordSetPage(ResidentialAddrType), recordSet)

        val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

        status(result) mustBe OK
        verify(mockJourneyCacheRepository, times(1)).get(any())
      }
    }

    "redirect to postcode lookup form" when {
      "RecordSet can not be retrieved from the cache" in new LocalSetup {
        override def currentRequest[A]: Request[A] =
          FakeRequest("POST", "")
            .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
            .asInstanceOf[Request[A]]

        override def userAnswersToReturn: UserAnswers = UserAnswers.empty("id")

        val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

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
      "supplied no addressId in the form" in new LocalSetup {
        override def currentRequest[A]: Request[A] =
          FakeRequest("POST", "")
            .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
            .asInstanceOf[Request[A]]

        override def userAnswersToReturn: UserAnswers =
          baseUserAnswers
            .setOrException(SelectedRecordSetPage(PostalAddrType), oneAndTwoOtherPlacePafRecordSet)

        val result: Future[Result] = controller.onSubmit(PostalAddrType)(FakeRequest())

        status(result) mustBe BAD_REQUEST
        verify(mockJourneyCacheRepository, times(1)).get(any())
      }

      "supplied no addressId in the form with a filter" in new LocalSetup {

        override def currentRequest[A]: Request[A] =
          FakeRequest("POST", "")
            .withFormUrlEncodedBody("postcode" -> "AA1 1AA", "filter" -> "7")
            .asInstanceOf[Request[A]]

        override def userAnswersToReturn: UserAnswers =
          baseUserAnswers
            .setOrException(SelectedRecordSetPage(PostalAddrType), oneAndTwoOtherPlacePafRecordSet)

        val result: Future[Result] = controller.onSubmit(PostalAddrType)(FakeRequest())

        status(result) mustBe BAD_REQUEST

        verify(mockJourneyCacheRepository, times(1)).get(any())
      }
    }

    "call the address lookup service and redirect to the edit address form for a postal address type when supplied with an addressId" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("addressId" -> "GB990091234514", "postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      override def userAnswersToReturn: UserAnswers =
        baseUserAnswers
          .setOrException(SelectedRecordSetPage(PostalAddrType), oneAndTwoOtherPlacePafRecordSet)

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/edit-address")

      val expectedUserAnswers: UserAnswers = baseUserAnswers
        .setOrException(SelectedRecordSetPage(PostalAddrType), RecordSet(Seq(oneOtherPlacePafAddressRecord)))

      verify(mockJourneyCacheRepository, times(1)).set(expectedUserAnswers)
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "call the address lookup service and return a 500 when an invalid addressId is supplied in the form" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("addressId" -> "GB000000000000", "postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      override def userAnswersToReturn: UserAnswers =
        baseUserAnswers
          .setOrException(SelectedRecordSetPage(ResidentialAddrType), oneAndTwoOtherPlacePafRecordSet)

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(FakeRequest())

      status(result) mustBe INTERNAL_SERVER_ERROR
      verify(mockJourneyCacheRepository, times(0)).set(any())
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "redirect to enter start date page if postcode is different to currently held postcode" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("addressId" -> "GB990091234515", "postcode" -> "AA1 2AA")
          .asInstanceOf[Request[A]]

      override def userAnswersToReturn: UserAnswers =
        baseUserAnswers
          .setOrException(SelectedRecordSetPage(ResidentialAddrType), oneAndTwoOtherPlacePafRecordSet)

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/enter-start-date")
    }

    "redirect to check and submit page if postcode is not different to currently held postcode" in new LocalSetup {

      override def userAnswersToReturn: UserAnswers =
        baseUserAnswers
          .setOrException(SelectedRecordSetPage(ResidentialAddrType), oneAndTwoOtherPlacePafRecordSet)

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("addressId" -> "GB990091234515", "postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")

      val expectedUserAnswers: UserAnswers = baseUserAnswers
        .setOrException(SubmittedStartDatePage(ResidentialAddrType), DateDto(LocalDate.now()))
      verify(mockJourneyCacheRepository, times(1)).set(expectedUserAnswers)
    }
  }
}
