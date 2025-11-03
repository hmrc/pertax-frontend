/*
 * Copyright 2025 HM Revenue & Customs
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
import cats.instances.future._
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.bindable.{PostalAddrType, ResidentialAddrType}
import models.dto.{AddressDto, AddressPageVisitedDto, InternationalAddressChoiceDto}
import models.{NonFilerSelfAssessmentUser, PersonDetails, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.Application
import play.api.inject.bind
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import repositories.JourneyCacheRepository
import routePages.{HasAddressAlreadyVisitedPage, SubmittedAddressPage, SubmittedInternationalAddressChoicePage}
import services.CitizenDetailsService
import testUtils.Fixtures.{buildPersonDetailsWithPersonalAndCorrespondenceAddress, fakeStreetTupleListAddressForUnmodified}
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class StartDateControllerSpec extends BaseSpec {

  def asAddressDto(l: List[(String, String)]): AddressDto = AddressDto.ukForm.bind(l.toMap).get
  val thisYearStr: String                                 = LocalDate.now().getYear.toString
  val personDetails: PersonDetails                        = buildPersonDetailsWithPersonalAndCorrespondenceAddress

  val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]
  val mockCitizenDetailsService: CitizenDetailsService   = mock[CitizenDetailsService]

  class FakeAuthAction extends AuthJourney {
    override def authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
      new ActionBuilder[UserRequest, AnyContent] {
        override def parser: BodyParser[AnyContent]                                                               = play.api.test.Helpers.stubBodyParser()
        override protected def executionContext: ExecutionContext                                                 = scala.concurrent.ExecutionContext.Implicits.global
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(buildUserRequest(saUser = NonFilerSelfAssessmentUser, request = request))
      }
  }

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthJourney].toInstance(new FakeAuthAction),
      bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository),
      bind[CitizenDetailsService].toInstance(mockCitizenDetailsService)
    )
    .build()

  def currentRequest[A]: Request[A] = FakeRequest("GET", "/test").asInstanceOf[Request[A]]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockJourneyCacheRepository)
    reset(mockCitizenDetailsService)
  }

  private lazy val controller: StartDateController = app.injector.instanceOf[StartDateController]

  "onPageLoad" must {
    "return 200 when passed ResidentialAddrType and submittedAddress is in cache" in {
      val addressDto: AddressDto   = asAddressDto(fakeStreetTupleListAddressForUnmodified)
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SubmittedAddressPage(ResidentialAddrType), addressDto)

      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT(Some(personDetails)))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(currentRequest)
      status(result) mustBe OK
    }

    "redirect to 'edit address' when passed PostalAddrType as this step is not valid for postal" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT(Some(personDetails)))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(currentRequest)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/edit-address")
    }
  }

  "onSubmit" must {

    "return 303 when passed ResidentialAddrType and a valid date" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT(Some(personDetails)))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      val request = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "1", "startDate.month" -> "1", "startDate.year" -> "2016")

      val result = controller.onSubmit(ResidentialAddrType)(request)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "return 400 when start date is in the future (P85 enabled)" in {
      val userAnswers = UserAnswers
        .empty("id")
        .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.OutsideUK)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT(Some(personDetails)))

      val request = FakeRequest("POST", "")
        .withFormUrlEncodedBody(
          "startDate.day"   -> "1",
          "startDate.month" -> "1",
          "startDate.year"  -> (LocalDate.now().getYear + 1).toString
        )

      val result = controller.onSubmit(ResidentialAddrType)(request)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("Complete a P85 form")
    }

    "return 400 when updated start date is not after recorded start date for international or cross-border (P85 messaging)" in {
      val existingAddress = personDetails.address.map(_.copy(startDate = Some(LocalDate.now().minusDays(1))))
      val person          = personDetails.copy(address = existingAddress)
      val userAnswers     = UserAnswers
        .empty("id")
        .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.OutsideUK)

      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT(Some(person)))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val request = FakeRequest("POST", "")
        .withFormUrlEncodedBody(
          "startDate.day"   -> LocalDate.now().minusDays(2).getDayOfMonth.toString,
          "startDate.month" -> LocalDate.now().getMonthValue.toString,
          "startDate.year"  -> LocalDate.now().getYear.toString
        )

      val result = controller.onSubmit(ResidentialAddrType)(request)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("Complete a P85 form")
    }

    "redirect successfully when startDate is after recorded for domestic addresses" in {
      val userAnswers = UserAnswers
        .empty("id")
        .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT(Some(personDetails)))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      val request = FakeRequest("POST", "")
        .withFormUrlEncodedBody(
          "startDate.day"   -> "10",
          "startDate.month" -> "10",
          "startDate.year"  -> LocalDate.now().getYear.toString
        )

      val result = controller.onSubmit(ResidentialAddrType)(request)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "return 400 when missing date fields" in {
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT(Some(personDetails)))

      val result = controller.onSubmit(ResidentialAddrType)(FakeRequest("POST", ""))
      status(result) mustBe BAD_REQUEST
    }
  }
}
