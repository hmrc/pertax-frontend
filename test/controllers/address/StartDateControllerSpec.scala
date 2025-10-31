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
import play.api.test.Helpers.{redirectLocation, *}
import repositories.JourneyCacheRepository
import routePages.{HasAddressAlreadyVisitedPage, SubmittedAddressPage, SubmittedInternationalAddressChoicePage}
import services.CitizenDetailsService
import testUtils.Fixtures.{buildPersonDetailsWithPersonalAndCorrespondenceAddress, fakeStreetTupleListAddressForUnmodified}
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec, Fixtures}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class StartDateControllerSpec extends BaseSpec {

  private def asAddressDto(l: List[(String, String)]): AddressDto = AddressDto.ukForm.bind(l.toMap).get

  val thisYearStr: String          = "2019"
  val personDetails: PersonDetails = buildPersonDetailsWithPersonalAndCorrespondenceAddress

  val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]
  val mockCitizenDetailsService: CitizenDetailsService   = mock[CitizenDetailsService]

  class FakeAuthAction extends AuthJourney {
    override def authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
      new ActionBuilder[UserRequest, AnyContent] {
        override def parser: BodyParser[AnyContent] = play.api.test.Helpers.stubBodyParser()
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(buildUserRequest(saUser = NonFilerSelfAssessmentUser, request = request))
        override protected def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
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
      val addressDto: AddressDto = asAddressDto(fakeStreetTupleListAddressForUnmodified)
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SubmittedAddressPage(ResidentialAddrType), addressDto)

      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result = controller.onPageLoad(ResidentialAddrType)(currentRequest)
      status(result) mustBe OK
    }

    "redirect to 'edit address' when passed PostalAddrType as this step is not valid for postal" in {
      val userAnswers: UserAnswers =
        UserAnswers.empty("id").setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))

      val result = controller.onPageLoad(PostalAddrType)(currentRequest)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/edit-address")
    }
  }

  "onSubmit" must {

    "return 303 when passed ResidentialAddrType and a valid form with low numbers" in {
      val userAnswers: UserAnswers =
        UserAnswers.empty("id").setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))

      val req: Request[_] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "1", "startDate.month" -> "1", "startDate.year" -> "2016")

      val result = controller.onSubmit(ResidentialAddrType)(req)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "return 303 when passed ResidentialAddrType and date is today" in {
      val userAnswers: UserAnswers =
        UserAnswers.empty("id").setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))

      val req: Request[_] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "2", "startDate.month" -> "2", "startDate.year" -> "2016")

      val result = controller.onSubmit(ResidentialAddrType)(req)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect (SEE_OTHER) when startDate is earlier than recorded with Residential (domestic; not cross-border)" in {
      // Domestic: explicitly mark as England (new address choice); earlier than NPS should override to today and proceed
      val userAnswers: UserAnswers =
        UserAnswers.empty("id")
          .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
          .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))

      val req: Request[_] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "14", "startDate.month" -> "03", "startDate.year" -> "2015")

      val result = controller.onSubmit(ResidentialAddrType)(req)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect (SEE_OTHER) when startDate is the same as recorded with Residential (domestic; not cross-border)" in {
      val userAnswers: UserAnswers =
        UserAnswers.empty("id")
          .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
          .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))

      val req: Request[_] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "15", "startDate.month" -> "03", "startDate.year" -> "2015")

      val result = controller.onSubmit(ResidentialAddrType)(req)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect to the changes page when passed ResidentialAddrType and a valid form with high numbers" in {
      val userAnswers: UserAnswers =
        UserAnswers.empty("id").setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))

      val req: Request[_] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "31", "startDate.month" -> "12", "startDate.year" -> thisYearStr)

      val result = controller.onSubmit(ResidentialAddrType)(req)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "return 400 when passed ResidentialAddrType and missing date fields" in {
      val userAnswers: UserAnswers = UserAnswers.empty("id")
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))

      val req: Request[_] = FakeRequest("POST", "").withFormUrlEncodedBody()

      val result = controller.onSubmit(ResidentialAddrType)(req)
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when passed ResidentialAddrType and day out of range - too early" in {
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))
      val req: Request[_] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "0", "startDate.month" -> "1", "startDate.year" -> thisYearStr)

      val result = controller.onSubmit(ResidentialAddrType)(req)
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when passed ResidentialAddrType and day out of range - too late" in {
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))
      val req: Request[_] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "32", "startDate.month" -> "1", "startDate.year" -> thisYearStr)

      val result = controller.onSubmit(ResidentialAddrType)(req)
      status(result) mustBe BAD_REQUEST
    }

    "return 400 with P85 messaging when updated start date is not after start date on record (international / OutsideUK)" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(
        _.copy(startDate = Some(LocalDate.of(2016, 11, 22)))
      )
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val pdOutsideUk   = PersonDetails(person, address, None)

      val userAnswers: UserAnswers =
        UserAnswers.empty("id")
          .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
          .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.OutsideUK)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(pdOutsideUk)))

      val req: Request[_] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "3", "startDate.month" -> "2", "startDate.year" -> "2016")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(buildUserRequest(request = req).asInstanceOf[UserRequest[A]])
      })

      val result = controller.onSubmit(ResidentialAddrType)(req)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("Complete a P85 form (opens in new tab)")
    }

    "return 400 with P85 messaging when the start date is after todays date (international / OutsideUK)" in {
      val userAnswers: UserAnswers =
        UserAnswers.empty("id")
          .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
          .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.OutsideUK)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))

      val req: Request[_] = FakeRequest("POST", "")
        .withFormUrlEncodedBody(
          "startDate.day"   -> "3",
          "startDate.month" -> "2",
          "startDate.year"  -> (LocalDate.now().getYear + 1).toString
        )

      val result = controller.onSubmit(ResidentialAddrType)(req)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("Complete a P85 form (opens in new tab)")
    }

    "redirect to correct successful url when supplied with startDate after recorded with Residential (domestic)" in {
      val userAnswers: UserAnswers =
        UserAnswers.empty("id")
          .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
          .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))

      val req: Request[_] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "16", "startDate.month" -> "03", "startDate.year" -> thisYearStr)

      val result = controller.onSubmit(ResidentialAddrType)(req)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect to correct successful url when supplied with startDate after startDate on record with Residential address" in {
      val userAnswers: UserAnswers =
        UserAnswers.empty("id")
          .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
          .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))

      val req: Request[_] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)

      val result = controller.onSubmit(ResidentialAddrType)(req)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect to success page when no startDate is on record" in {
      val userAnswers: UserAnswers =
        UserAnswers.empty("id")
          .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
          .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))

      val req: Request[_] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)

      val result = controller.onSubmit(ResidentialAddrType)(req)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "return 400 when not after NPS for cross-border (Scotland <-> rest of UK)" in {
      // Old = Scotland; New choice = England -> cross-border; same date as NPS => BAD_REQUEST
      val scottishAddress = Fixtures
        .buildPersonDetailsWithPersonalAndCorrespondenceAddress
        .address
        .map(_.copy(country = Some("SCOTLAND"), startDate = Some(LocalDate.of(2015, 3, 15))))
      val scottishPD = PersonDetails(
        Fixtures.buildPersonDetailsWithPersonalAndCorrespondenceAddress.person,
        scottishAddress,
        None
      )

      val userAnswers: UserAnswers =
        UserAnswers.empty("id")
          .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
          .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(scottishPD)))

      val req: Request[_] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "15", "startDate.month" -> "03", "startDate.year" -> "2015")

      val result = controller.onSubmit(ResidentialAddrType)(req)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) mustNot include("Complete a P85 form")
    }
  }
}
