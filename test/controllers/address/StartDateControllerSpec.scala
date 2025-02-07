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

import cats.data.EitherT
import controllers.auth.requests.UserRequest
import controllers.bindable.{PostalAddrType, ResidentialAddrType}
import models.dto.{AddressDto, AddressPageVisitedDto, InternationalAddressChoiceDto}
import models.{PersonDetails, UserAnswers}
import org.mockito.ArgumentMatchers.any
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import routePages.{HasAddressAlreadyVisitedPage, SubmittedAddressPage, SubmittedInternationalAddressChoicePage}
import testUtils.{ActionBuilderFixture, Fixtures}
import testUtils.Fixtures.fakeStreetTupleListAddressForUnmodified
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.fixtures.AddressFixture.{address => addressFixture}
import testUtils.fixtures.PersonFixture._
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import scala.concurrent.Future

class StartDateControllerSpec extends AddressBaseSpec {
  private lazy val controller: StartDateController = app.injector.instanceOf[StartDateController]

  "onPageLoad" must {
    "return 200 when passed ResidentialAddrType and submittedAddress is in cache" in {
      val addressDto: AddressDto   = asAddressDto(fakeStreetTupleListAddressForUnmodified)
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SubmittedAddressPage(ResidentialAddrType), addressDto)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(currentRequest)

      status(result) mustBe OK
    }

    "redirect to 'edit address' when passed PostalAddrType as this step is not valid for postal" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/edit-address")
    }
  }

  "onSubmit" must {

    "return 303 when passed ResidentialAddrType and a valid form with low numbers" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "1", "startDate.month" -> "1", "startDate.year" -> "2016")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "return 303 when passed ResidentialAddrType and date is in the today" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "2", "startDate.month" -> "2", "startDate.year" -> "2016")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect to the changes to residential address page when passed ResidentialAddrType and a valid form with high numbers" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "31", "startDate.month" -> "12", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "return 400 when passed ResidentialAddrType and missing date fields" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "").withFormUrlEncodedBody().asInstanceOf[Request[A]]

      val result: Future[Result] =
        controller.onSubmit(ResidentialAddrType)(currentRequest)
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when passed ResidentialAddrType and day out of range - too early" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "0", "startDate.month" -> "1", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when passed ResidentialAddrType and day out of range - too late" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "32", "startDate.month" -> "1", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe BAD_REQUEST
    }

    "return 400 when passed ResidentialAddrType and month out of range at lower bound" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "1", "startDate.month" -> "0", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when passed ResidentialAddrType and month out of range at upper bound" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "31", "startDate.month" -> "13", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)
      status(result) mustBe BAD_REQUEST
    }

    "return 400 with P85 messaging when passed ResidentialAddrType and the updated start date is not after the start date on record (international address)" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(
        _.copy(startDate = Some(LocalDate.of(2016, 11, 22)))
      )
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails(person, address, None)
      when(mockCitizenDetailsService.personDetails(any())(any(), any())).thenReturn(
        EitherT.rightT(personDetails)
      )

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.OutsideUK)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "3", "startDate.month" -> "2", "startDate.year" -> "2016")
          .asInstanceOf[Request[A]]

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = currentRequest,
              personDetails = Some(
                PersonDetails(emptyPerson, Some(addressFixture(startDate = Some(LocalDate.of(2016, 11, 22)))), None)
              )
            ).asInstanceOf[UserRequest[A]]
          )
      })

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("Complete a P85 form (opens in new tab)")
    }

    "return 400 with P85 messaging when passed ResidentialAddrType and the start date is after todays date (international address)" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.OutsideUK)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody(
            "startDate.day"   -> "3",
            "startDate.month" -> "2",
            "startDate.year"  -> (LocalDate.now().getYear + 1).toString
          )
          .asInstanceOf[Request[A]]

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = currentRequest,
              personDetails = Some(
                PersonDetails(emptyPerson, Some(addressFixture(startDate = Some(LocalDate.of(2016, 11, 22)))), None)
              )
            ).asInstanceOf[UserRequest[A]]
          )
      })

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("Complete a P85 form (opens in new tab)")
    }

    "return a 400 without p85 messaging when startDate is earlier than recorded with residential address type (domestic address)" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "14", "startDate.month" -> "03", "startDate.year" -> "2015")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe BAD_REQUEST
      contentAsString(result) mustNot include("Complete a P85 form (opens in new tab)")
    }

    "return a 400 when startDate is the same as recorded with residential address type" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "15", "startDate.month" -> "03", "startDate.year" -> "2015")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe BAD_REQUEST
    }

    "return a 400 when startDate is earlier than recorded with Residential address type" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "14", "startDate.month" -> "03", "startDate.year" -> "2015")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe BAD_REQUEST
    }

    "return a 400 when startDate is the same as recorded with Residential address type" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "15", "startDate.month" -> "03", "startDate.year" -> "2015")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe BAD_REQUEST
    }

    "redirect to correct successful url when supplied with startDate after recorded with residential address type" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "16", "startDate.month" -> "03", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect to correct successful url when supplied with startDate after startDate on record with Residential address" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect to success page when no startDate is on record" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect to success page when no address is on record" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }
  }
}
