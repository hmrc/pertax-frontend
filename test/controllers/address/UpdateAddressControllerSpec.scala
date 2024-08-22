/*
 * Copyright 2024 HM Revenue & Customs
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
import models.UserAnswers
import models.addresslookup.{Address, AddressRecord, Country}
import models.dto.{AddressPageVisitedDto, DateDto}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers.any
import play.api.http.Status.{BAD_REQUEST, OK, SEE_OTHER}
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import routePages.{AddressLookupServiceDownPage, HasAddressAlreadyVisitedPage, SelectedAddressRecordPage, SubmittedAddressPage, SubmittedStartDatePage}
import testUtils.Fixtures.{fakeStreetPafAddressRecord, fakeStreetTupleListAddressForUnmodified}
import views.html.personaldetails.UpdateAddressView

import java.time.LocalDate
import scala.concurrent.Future

class UpdateAddressControllerSpec extends AddressBaseSpec {

  trait LocalSetup extends AddressControllerSetup {

    def controller: UpdateAddressController =
      new UpdateAddressController(
        addressJourneyCachingHelper,
        mockAuthJourney,
        cc,
        inject[UpdateAddressView],
        displayAddressInterstitialView,
        mockFeatureFlagService,
        internalServerErrorView
      )

    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]
  }

  "onPageLoad" must {

    "find only the selected address from the session cache and no residency choice and return 303" in new LocalSetup {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(ResidentialAddrType), fakeStreetPafAddressRecord)

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "fetch the selected address and page visited true has been selected from the session cache and return 200" in new LocalSetup {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(ResidentialAddrType), fakeStreetPafAddressRecord)
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "find no selected address with residential address type but addressPageVisitedDTO in the session cache and still return 200" in new LocalSetup {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "find no residency choice in the session cache and redirect to the beginning of the journey" in new LocalSetup {

      val userAnswers: UserAnswers = UserAnswers.empty("id")

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      verify(mockJourneyCacheRepository, times(1)).get(any())
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
    }

    "redirect user to beginning of journey and return 303 for postal addressType and no pagevisitedDto in cache" in new LocalSetup {

      val userAnswers: UserAnswers = UserAnswers.empty
      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      verify(mockJourneyCacheRepository, times(1)).get(any())
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
    }

    "display edit address page and return 200 for postal addressType with pagevisitedDto and addressRecord in cache" in new LocalSetup {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(PostalAddrType), fakeStreetPafAddressRecord)
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "display edit address page and return 200 for postal addressType with pagevisitedDto and no addressRecord in cache" in new LocalSetup {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "find no addresses in the session cache and return 303" in new LocalSetup {
      val userAnswers: UserAnswers = UserAnswers.empty("id")

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "find residential selected and submitted addresses in the session cache and return 200" in new LocalSetup {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(ResidentialAddrType), fakeStreetPafAddressRecord)
        .setOrException(
          SubmittedAddressPage(ResidentialAddrType),
          asAddressDto(fakeStreetTupleListAddressForUnmodified)
        )
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "find no selected address but a submitted address in the session cache and return 200" in new LocalSetup {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(
          SubmittedAddressPage(ResidentialAddrType),
          asAddressDto(fakeStreetTupleListAddressForUnmodified)
        )
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "show 'Enter the address' when user amends correspondence address manually and address has not been selected" in new LocalSetup {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
      val doc: Document = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("govuk-fieldset__heading").toString.contains("Your postal address") mustBe true
    }

    "show 'Enter your address' when user amends residential address manually and address has not been selected" in new LocalSetup {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
      val doc: Document = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("govuk-fieldset__heading").toString.contains("Your address") mustBe true
    }

    "show 'Edit the address (optional)' when user amends correspondence address manually and address has been selected" in new LocalSetup {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(PostalAddrType), fakeStreetPafAddressRecord)
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
      val doc: Document = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("govuk-fieldset__heading").toString.contains("Edit the address (optional)") mustBe true
    }

    "show 'Edit your address (optional)' when user amends residential address manually and address has been selected" in new LocalSetup {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(ResidentialAddrType), fakeStreetPafAddressRecord)
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
      val doc: Document = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("govuk-fieldset__heading").toString.contains("Edit your address (optional)") mustBe true
    }
  }

  "onSubmit" must {

    "return 400 when supplied invalid form input" in new LocalSetup {

      val emptyAddress: Address        = Address(List(""), None, None, "", None, Country("", ""))
      val addressRecord: AddressRecord = AddressRecord("", emptyAddress, "")
      val userAnswers: UserAnswers     = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(PostalAddrType), addressRecord)

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(FakeRequest("POST", ""))

      status(result) mustBe BAD_REQUEST
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "return 303, caching addressDto and redirecting to enter start date page when supplied valid form input on a postal journey" in new LocalSetup {

      val userAnswers: UserAnswers = UserAnswers.empty("id").setOrException(AddressLookupServiceDownPage, true)

      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody(fakeStreetTupleListAddressForUnmodified: _*)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/changes")

      val ua: UserAnswers = UserAnswers.empty
        .setOrException(SubmittedAddressPage(PostalAddrType), asAddressDto(fakeStreetTupleListAddressForUnmodified))
      verify(mockJourneyCacheRepository, times(1)).set(ua)
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "return 303, caching addressDto and redirecting to review changes page when supplied valid form input on a non postal journey and input default startDate into cache" in new LocalSetup {

      val userAnswers: UserAnswers = UserAnswers.empty("id").setOrException(AddressLookupServiceDownPage, true)
      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(userAnswers))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody(fakeStreetTupleListAddressForUnmodified: _*)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")

      val ua: UserAnswers = UserAnswers.empty
        .setOrException(
          SubmittedAddressPage(ResidentialAddrType),
          asAddressDto(fakeStreetTupleListAddressForUnmodified)
        )
        .setOrException(SubmittedStartDatePage(ResidentialAddrType), DateDto(LocalDate.now()))
      verify(mockJourneyCacheRepository, times(1)).set(ua)
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }
  }
}
