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
import models.dto.AddressPageVisitedDto
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers.any
import play.api.http.Status.{BAD_REQUEST, OK, SEE_OTHER}
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import routePages.{AddressLookupServiceDownPage, HasAddressAlreadyVisitedPage, SelectedAddressRecordPage, SubmittedAddressPage}
import testUtils.Fixtures.{fakeStreetPafAddressRecord, fakeStreetTupleListAddressForUnmodified}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class UpdateAddressControllerSpec extends AddressBaseSpec {
  private def controller: UpdateAddressController = app.injector.instanceOf[UpdateAddressController]

  "onPageLoad" must {

    "find only the selected address from the session cache and no residency choice and return 303" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(ResidentialAddrType), fakeStreetPafAddressRecord)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
    }

    "fetch the selected address and page visited true has been selected from the session cache and return 200" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(ResidentialAddrType), fakeStreetPafAddressRecord)
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
    }

    "find no selected address with residential address type but addressPageVisitedDTO in the session cache and still return 200" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
    }

    "find no residency choice in the session cache and redirect to the beginning of the journey" in {

      val userAnswers: UserAnswers = UserAnswers.empty("id")

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
    }

    "redirect user to beginning of journey and return 303 for postal addressType and no pagevisitedDto in cache" in {

      val userAnswers: UserAnswers = UserAnswers.empty
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
    }

    "display edit address page and return 200 for postal addressType with pagevisitedDto and addressRecord in cache" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(PostalAddrType), fakeStreetPafAddressRecord)
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
    }

    "display edit address page and return 200 for postal addressType with pagevisitedDto and no addressRecord in cache" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
    }

    "find no addresses in the session cache and return 303" in {
      val userAnswers: UserAnswers = UserAnswers.empty("id")

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
    }

    "find residential selected and submitted addresses in the session cache and return 200" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(ResidentialAddrType), fakeStreetPafAddressRecord)
        .setOrException(
          SubmittedAddressPage(ResidentialAddrType),
          asAddressDto(fakeStreetTupleListAddressForUnmodified)
        )
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
    }

    "find no selected address but a submitted address in the session cache and return 200" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(
          SubmittedAddressPage(ResidentialAddrType),
          asAddressDto(fakeStreetTupleListAddressForUnmodified)
        )
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
    }

    "show 'Enter the address' when user amends correspondence address manually and address has not been selected" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      val doc: Document = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("govuk-fieldset__heading").toString.contains("Your postal address") mustBe true
    }

    "show 'Enter your address' when user amends residential address manually and address has not been selected" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      val doc: Document = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("govuk-fieldset__heading").toString.contains("Your address") mustBe true
    }

    "show 'Edit the address (optional)' when user amends correspondence address manually and address has been selected" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(PostalAddrType), fakeStreetPafAddressRecord)
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      val doc: Document = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("govuk-fieldset__heading").toString.contains("Edit the address (optional)") mustBe true
    }

    "show 'Edit your address (optional)' when user amends residential address manually and address has been selected" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(ResidentialAddrType), fakeStreetPafAddressRecord)
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      val doc: Document = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("govuk-fieldset__heading").toString.contains("Edit your address (optional)") mustBe true
    }
  }

  "onSubmit" must {

    "return 400 when supplied invalid form input" in {

      val emptyAddress: Address        = Address(List(""), None, None, "", None, Country("", ""))
      val addressRecord: AddressRecord = AddressRecord("", emptyAddress, "")
      val userAnswers: UserAnswers     = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(PostalAddrType), addressRecord)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(FakeRequest("POST", ""))

      status(result) mustBe BAD_REQUEST
    }

    "return 303, caching addressDto and redirecting to enter start date page when supplied valid form input on a postal journey" in {

      val userAnswers: UserAnswers = UserAnswers.empty("id").setOrException(AddressLookupServiceDownPage, true)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody(fakeStreetTupleListAddressForUnmodified: _*)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/changes")
    }

    "return 303, caching addressDto and redirecting to review changes page when supplied valid form input on a non postal journey and input default startDate into cache" in {

      val userAnswers: UserAnswers = UserAnswers.empty("id").setOrException(AddressLookupServiceDownPage, true)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody(fakeStreetTupleListAddressForUnmodified: _*)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }
  }
}
