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
import services.{AddressCountryService, CitizenDetailsService, NormalizationUtils, StartDateDecisionService}
import testUtils.Fixtures.{buildPersonDetailsWithPersonalAndCorrespondenceAddress, fakeStreetTupleListAddressForUnmodified}
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class StartDateControllerSpec extends BaseSpec {
  def asAddressDto(l: List[(String, String)]): AddressDto = AddressDto.ukForm.bind(l.toMap).get

  val thisYearStr: String          = "2019"
  val personDetails: PersonDetails = buildPersonDetailsWithPersonalAndCorrespondenceAddress

  val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]
  val mockCitizenDetailsService: CitizenDetailsService   = mock[CitizenDetailsService]
  val mockAddressCountryService: AddressCountryService   = mock[AddressCountryService]
  val normalizationUtils: NormalizationUtils             = new NormalizationUtils
  val startDateDecisionService: StartDateDecisionService = new StartDateDecisionService

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
      bind[CitizenDetailsService].toInstance(mockCitizenDetailsService),
      bind[AddressCountryService].toInstance(mockAddressCountryService),
      bind[NormalizationUtils].toInstance(normalizationUtils),
      bind[StartDateDecisionService].toInstance(startDateDecisionService)
    )
    .build()

  def currentRequest[A]: Request[A] = FakeRequest("GET", "/test").asInstanceOf[Request[A]]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockJourneyCacheRepository)
    reset(mockCitizenDetailsService)
    reset(mockAddressCountryService)
  }

  private lazy val controller: StartDateController = app.injector.instanceOf[StartDateController]

  "onPageLoad" must {
    "return 200 when passed ResidentialAddrType and submittedAddress is in cache" in {
      val addressDto: AddressDto   = asAddressDto(fakeStreetTupleListAddressForUnmodified)
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SubmittedAddressPage(ResidentialAddrType), addressDto)

      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(currentRequest)

      status(result) mustBe OK
    }

    "redirect to 'edit address' when passed PostalAddrType as this step is not valid for postal" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )

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
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )
      when(mockAddressCountryService.isCrossBorderScotland(any(), any())(any())).thenReturn(Future.successful(false))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "1", "startDate.month" -> "1", "startDate.year" -> "2016")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "return 303 when passed ResidentialAddrType and date is in the today" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )
      when(mockAddressCountryService.isCrossBorderScotland(any(), any())(any())).thenReturn(Future.successful(false))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "2", "startDate.month" -> "2", "startDate.year" -> "2016")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect to the changes to residential address page when passed ResidentialAddrType and a valid form with high numbers" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )
      when(mockAddressCountryService.isCrossBorderScotland(any(), any())(any())).thenReturn(Future.successful(false))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "31", "startDate.month" -> "12", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "return 400 when passed ResidentialAddrType and missing date fields" in {
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )

      def postReq[A]: Request[A] =
        FakeRequest("POST", "").withFormUrlEncodedBody().asInstanceOf[Request[A]]

      val result: Future[Result] =
        controller.onSubmit(ResidentialAddrType)(postReq)
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when passed ResidentialAddrType and day out of range - too early" in {
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "0", "startDate.month" -> "1", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when passed ResidentialAddrType and day out of range - too late" in {
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "32", "startDate.month" -> "1", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)

      status(result) mustBe BAD_REQUEST
    }

    "return 400 when passed ResidentialAddrType and month out of range at lower bound" in {
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "1", "startDate.month" -> "0", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)
      status(result) mustBe BAD_REQUEST
    }

    "return 400 when passed ResidentialAddrType and month out of range at upper bound" in {
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "31", "startDate.month" -> "13", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)
      status(result) mustBe BAD_REQUEST
    }

    "return 400 with P85 messaging when passed ResidentialAddrType and the updated start date is not after the start date on record (international address)" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(
        _.copy(startDate = Some(LocalDate.of(2016, 11, 22)))
      )
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails("115", person, address, None)

      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.OutsideUK)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockAddressCountryService.isCrossBorderScotland(any(), any())(any())).thenReturn(Future.successful(false))

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "3", "startDate.month" -> "2", "startDate.year" -> "2016")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("Complete a P85 form (opens in new tab)")
    }

    "return 400 with P85 messaging when passed ResidentialAddrType and the start date is after todays date (international address)" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.OutsideUK)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )
      when(mockAddressCountryService.isCrossBorderScotland(any(), any())(any())).thenReturn(Future.successful(false))

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody(
            "startDate.day"   -> "3",
            "startDate.month" -> "2",
            "startDate.year"  -> (LocalDate.now().getYear + 1).toString
          )
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("Complete a P85 form (opens in new tab)")
    }

    "redirect (no P85 messaging) when startDate is earlier than recorded with residential address type (domestic address)" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )
      when(mockAddressCountryService.isCrossBorderScotland(any(), any())(any())).thenReturn(Future.successful(false))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "14", "startDate.month" -> "03", "startDate.year" -> "2015")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)

      status(result) mustBe SEE_OTHER
      contentAsString(result) mustNot include("Complete a P85 form (opens in new tab)")
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect when startDate is the same as recorded with residential address type (domestic address)" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )
      when(mockAddressCountryService.isCrossBorderScotland(any(), any())(any())).thenReturn(Future.successful(false))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "15", "startDate.month" -> "03", "startDate.year" -> "2015")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect to correct successful url when supplied with startDate after recorded with residential address type" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )
      when(mockAddressCountryService.isCrossBorderScotland(any(), any())(any())).thenReturn(Future.successful(false))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "16", "startDate.month" -> "03", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect to correct successful url when supplied with startDate after startDate on record with Residential address" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )
      when(mockAddressCountryService.isCrossBorderScotland(any(), any())(any())).thenReturn(Future.successful(false))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect to success page when no startDate is on record" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )
      when(mockAddressCountryService.isCrossBorderScotland(any(), any())(any())).thenReturn(Future.successful(false))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }

    "redirect to success page when no address is on record" in {
      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
          Future.successful(Right(Some(personDetails)))
        )
      )
      when(mockAddressCountryService.isCrossBorderScotland(any(), any())(any())).thenReturn(Future.successful(false))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      def postReq[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(postReq)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/changes")
    }
  }
}
