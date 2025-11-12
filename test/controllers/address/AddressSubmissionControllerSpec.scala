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

import cats.data.EitherT
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.bindable.{PostalAddrType, ResidentialAddrType}
import models.dto.{AddressDto, DateDto, InternationalAddressChoiceDto}
import models.{NonFilerSelfAssessmentUser, PersonDetails, UserAnswers}
import controllers.controllershelpers.AddressSubmissionControllerHelper
import uk.gov.hmrc.http.SessionKeys
import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.Application
import play.api.http.Status.OK
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.JourneyCacheRepository
import routePages.{SelectedAddressRecordPage, SubmittedAddressPage, SubmittedInternationalAddressChoicePage, SubmittedStartDatePage}
import services.CitizenDetailsService
import testUtils.Fixtures.*
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.model.DataEvent
import play.api.mvc.Results.Ok
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class AddressSubmissionControllerSpec extends BaseSpec {
  implicit lazy val messages: Messages = play.api.i18n.MessagesImpl(
    play.api.i18n.Lang.defaultLang,
    app.injector.instanceOf[play.api.i18n.MessagesApi]
  )

  class FakeAuthAction extends AuthJourney {
    override def authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
      new ActionBuilder[UserRequest, AnyContent] {
        override def parser: BodyParser[AnyContent] = play.api.test.Helpers.stubBodyParser()

        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(buildUserRequest(saUser = NonFilerSelfAssessmentUser, request = request))

        override protected def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      }
  }

  val mockJourneyCacheRepository: JourneyCacheRepository                       = mock[JourneyCacheRepository]
  val mockCitizenDetailsService: CitizenDetailsService                         = mock[CitizenDetailsService]
  val mockAddressSubmissionControllerHelper: AddressSubmissionControllerHelper = mock[AddressSubmissionControllerHelper]

  override implicit lazy val app: Application              = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthJourney].toInstance(new FakeAuthAction),
      bind[CitizenDetailsService].toInstance(mockCitizenDetailsService),
      bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository),
      bind[AddressSubmissionControllerHelper].toInstance(mockAddressSubmissionControllerHelper)
    )
    .build()
  private lazy val controller: AddressSubmissionController = app.injector.instanceOf[AddressSubmissionController]

  val personDetails: PersonDetails = buildPersonDetailsWithPersonalAndCorrespondenceAddress
  val nino: Nino                   = personDetails.person.nino.get

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockJourneyCacheRepository)
    reset(mockCitizenDetailsService)
    reset(mockAddressSubmissionControllerHelper)

    when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
      EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
        Future.successful(Right(Some(personDetails)))
      )
    )
  }

  def currentRequest =
    fakeScaRequest("GET", "")
      .withSession(SessionKeys.sessionId -> UUID.randomUUID().toString, SessionKeys.authToken -> "1")

  def fakePOSTRequest =
    fakeScaRequest("POST", "/test")
      .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
      .withSession(SessionKeys.sessionId -> UUID.randomUUID().toString, SessionKeys.authToken -> "1")

  protected def pruneDataEvent(dataEvent: DataEvent): DataEvent =
    dataEvent
      .copy(tags = dataEvent.tags - "X-Request-Chain" - "X-Session-ID" - "token", detail = dataEvent.detail - "credId")

  "onPageLoad" must {
    "return 200 if only submittedAddress is present in cache for postal address type" in {
      val addressDto: AddressDto = asAddressDto(fakeStreetTupleListAddressForUnmodified)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(
        Future.successful(
          UserAnswers
            .empty("id")
            .setOrException(SubmittedAddressPage(PostalAddrType), addressDto)
            .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)
        )
      )

      when(mockAddressSubmissionControllerHelper.isSubmittedAddressStartDateValid(any(), any())).thenReturn(
        true
      )

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(currentRequest)

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "redirect back to start of journey if submittedAddress is missing from cache for non-postal" in {
      val addressDto: AddressDto = asAddressDto(fakeStreetTupleListAddressForUnmodified)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(
        Future.successful(
          UserAnswers.empty("id").setOrException(SubmittedAddressPage(ResidentialAddrType), addressDto)
        )
      )

      when(mockAddressSubmissionControllerHelper.isSubmittedAddressStartDateValid(any(), any())).thenReturn(
        true
      )

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "redirect back to start of journey if submittedAddress is missing from cache for postal" in {
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(
        Future.successful(UserAnswers.empty("id"))
      )

      when(mockAddressSubmissionControllerHelper.isSubmittedAddressStartDateValid(any(), any())).thenReturn(
        true
      )

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }
    "display the appropriate label for address when the residential address has changed" in {
      val addressDtoWithUpdatedPostcode: AddressDto = asAddressDto(fakeStreetTupleListAddressForModifiedPostcode)
      val submittedStartDateDto: DateDto            = DateDto.build(15, 3, 2015)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(
        Future.successful(
          UserAnswers
            .empty("id")
            .setOrException(SubmittedAddressPage(ResidentialAddrType), addressDtoWithUpdatedPostcode)
            .setOrException(SubmittedStartDatePage(ResidentialAddrType), submittedStartDateDto)
            .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)
        )
      )

      when(mockAddressSubmissionControllerHelper.isSubmittedAddressStartDateValid(any(), any())).thenReturn(
        true
      )

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(currentRequest)

      contentAsString(result) must include(Messages("label.your_new_address"))
      contentAsString(result) must include(Messages("label.when_you_started_living_here"))
    }

    "display the appropriate label for address when the residential address has not changed" in {
      val addressDto: AddressDto         = asAddressDto(fakeStreetTupleListAddressForUnmodified)
      val submittedStartDateDto: DateDto = DateDto.build(15, 3, 2015)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(
        Future.successful(
          UserAnswers
            .empty("id")
            .setOrException(SubmittedAddressPage(ResidentialAddrType), addressDto)
            .setOrException(SubmittedStartDatePage(ResidentialAddrType), submittedStartDateDto)
            .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)
        )
      )

      when(mockAddressSubmissionControllerHelper.isSubmittedAddressStartDateValid(any(), any())).thenReturn(
        true
      )

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(currentRequest)

      contentAsString(result) must include(Messages("label.your_address"))
      contentAsString(result) mustNot include(Messages("label.when_you_started_living_here"))
    }
  }

  "onSubmit" must {

    "Return an error if ResidentialSubmittedStartDate is missing from the cache, and the journey type is ResidentialAddrType" in {
      val addressDto: AddressDto = asAddressDto(fakeStreetTupleListAddressForUnmodified)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(
        Future.successful(
          UserAnswers
            .empty("id")
            .setOrException(SubmittedAddressPage(ResidentialAddrType), addressDto)
        )
      )
      when(mockAddressSubmissionControllerHelper.isSubmittedAddressStartDateValid(any(), any())).thenReturn(
        false
      )

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(fakePOSTRequest)

      status(result) mustBe INTERNAL_SERVER_ERROR

      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "render the thank-you page if postalSubmittedStartDate is not in the cache, and the journey type is PostalAddrType" in {
      when(mockCitizenDetailsService.updateAddress(any(), any(), any(), any())(any(), any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](true)
      )

      val addressDto: AddressDto = asAddressDto(fakeStreetTupleListAddressForUnmodified)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(
        Future.successful(
          UserAnswers
            .empty("id")
            .setOrException(SubmittedAddressPage(PostalAddrType), addressDto)
        )
      )
      when(mockAddressSubmissionControllerHelper.isSubmittedAddressStartDateValid(any(), any())).thenReturn(
        true
      )
      when(
        mockAddressSubmissionControllerHelper
          .updateCitizenDetailsAddress(any(), any(), any(), any(), any())(any(), any(), any())
      ).thenReturn(
        Future.successful(Ok("Success"))
      )

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(fakePOSTRequest)

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
      verify(mockAddressSubmissionControllerHelper, times(1))
        .updateCitizenDetailsAddress(meq(nino), meq(PostalAddrType), any(), meq(personDetails), any())(
          any(),
          any(),
          any()
        )
    }

    "returns an error if residentialSubmittedAddress is missing from the cache" in {
      val submittedStartDateDto: DateDto = DateDto.build(15, 3, 2015)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(
        Future.successful(
          UserAnswers
            .empty("id")
            .setOrException(SubmittedStartDatePage(ResidentialAddrType), submittedStartDateDto)
        )
      )
      when(mockAddressSubmissionControllerHelper.isSubmittedAddressStartDateValid(any(), any())).thenReturn(
        true
      )

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(fakePOSTRequest)

      status(result) mustBe INTERNAL_SERVER_ERROR

      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "render the thank-you page and log a postcodeAddressSubmitted audit event upon successful submission of an unmodified address" in {
      val addressDto: AddressDto         = asAddressDto(fakeStreetTupleListAddressForUnmodifiedNoRegion)
      val submittedStartDateDto: DateDto = DateDto.build(15, 3, 2015)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(
        Future.successful(
          UserAnswers
            .empty("id")
            .setOrException(SelectedAddressRecordPage(ResidentialAddrType), fakeStreetPafAddressRecord)
            .setOrException(SubmittedAddressPage(ResidentialAddrType), addressDto)
            .setOrException(SubmittedStartDatePage(ResidentialAddrType), submittedStartDateDto)
        )
      )
      when(mockAddressSubmissionControllerHelper.isSubmittedAddressStartDateValid(any(), any())).thenReturn(
        true
      )
      when(
        mockAddressSubmissionControllerHelper
          .updateCitizenDetailsAddress(any(), any(), any(), any(), any())(any(), any(), any())
      ).thenReturn(
        Future.successful(Ok("Success"))
      )

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(fakePOSTRequest)

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
      verify(mockAddressSubmissionControllerHelper, times(1))
        .updateCitizenDetailsAddress(meq(nino), meq(ResidentialAddrType), any(), meq(personDetails), any())(
          any(),
          any(),
          any()
        )
    }

    "render the thank you page and log a postcodeAddressSubmitted audit event upon successful submission of an unmodified address, this time using postal type and having no postalSubmittedStartDate in the cache " in {
      val addressDto: AddressDto = asAddressDto(fakeStreetTupleListAddressForUnmodifiedNoRegion)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(
        Future.successful(
          UserAnswers
            .empty("id")
            .setOrException(SelectedAddressRecordPage(PostalAddrType), fakeStreetPafAddressRecord)
            .setOrException(SubmittedAddressPage(PostalAddrType), addressDto)
        )
      )
      when(mockAddressSubmissionControllerHelper.isSubmittedAddressStartDateValid(any(), any())).thenReturn(
        true
      )
      when(
        mockAddressSubmissionControllerHelper
          .updateCitizenDetailsAddress(any(), any(), any(), any(), any())(any(), any(), any())
      ).thenReturn(
        Future.successful(Ok("Success"))
      )

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody(fakeStreetTupleListAddressForUnmodified: _*)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(currentRequest)

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
      verify(mockAddressSubmissionControllerHelper, times(1))
        .updateCitizenDetailsAddress(meq(nino), meq(PostalAddrType), any(), meq(personDetails), any())(
          any(),
          any(),
          any()
        )
    }

    "render the thank you page and log a manualAddressSubmitted audit event upon successful submission of a manually entered address" in {
      val addressDto: AddressDto         = asAddressDto(fakeStreetTupleListAddressForManuallyEntered)
      val submittedStartDateDto: DateDto = DateDto.build(15, 3, 2015)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(
        Future.successful(
          UserAnswers
            .empty("id")
            .setOrException(SubmittedAddressPage(ResidentialAddrType), addressDto)
            .setOrException(SubmittedStartDatePage(ResidentialAddrType), submittedStartDateDto)
        )
      )
      when(mockAddressSubmissionControllerHelper.isSubmittedAddressStartDateValid(any(), any())).thenReturn(
        true
      )
      when(
        mockAddressSubmissionControllerHelper
          .updateCitizenDetailsAddress(any(), any(), any(), any(), any())(any(), any(), any())
      ).thenReturn(
        Future.successful(Ok("Success"))
      )

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(fakePOSTRequest)

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
      verify(mockAddressSubmissionControllerHelper, times(1))
        .updateCitizenDetailsAddress(meq(nino), meq(ResidentialAddrType), any(), meq(personDetails), any())(
          any(),
          any(),
          any()
        )
    }

    "render the thank you page and log a postcodeAddressModifiedSubmitted audit event upon successful of a modified address" in {
      val addressDto: AddressDto         = asAddressDto(fakeStreetTupleListAddressForModified)
      val submittedStartDateDto: DateDto = DateDto.build(15, 3, 2015)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(
        Future.successful(
          UserAnswers
            .empty("id")
            .setOrException(SelectedAddressRecordPage(ResidentialAddrType), fakeStreetPafAddressRecord)
            .setOrException(SubmittedAddressPage(ResidentialAddrType), addressDto)
            .setOrException(SubmittedStartDatePage(ResidentialAddrType), submittedStartDateDto)
            .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)
        )
      )
      when(mockAddressSubmissionControllerHelper.isSubmittedAddressStartDateValid(any(), any())).thenReturn(
        true
      )
      when(
        mockAddressSubmissionControllerHelper
          .updateCitizenDetailsAddress(any(), any(), any(), any(), any())(any(), any(), any())
      ).thenReturn(
        Future.successful(Ok("Success"))
      )

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(fakePOSTRequest)

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
      verify(mockAddressSubmissionControllerHelper, times(1))
        .updateCitizenDetailsAddress(meq(nino), meq(ResidentialAddrType), any(), meq(personDetails), any())(
          any(),
          any(),
          any()
        )
    }

    "render the confirmation page without the P85 messaging when updating a UK address" in {
      val addressDto: AddressDto         = asAddressDto(fakeStreetTupleListAddressForModified)
      val submittedStartDateDto: DateDto = DateDto.build(15, 3, 2015)
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(
        Future.successful(
          UserAnswers
            .empty("id")
            .setOrException(SelectedAddressRecordPage(ResidentialAddrType), fakeStreetPafAddressRecordOutsideUk)
            .setOrException(SubmittedAddressPage(ResidentialAddrType), addressDto)
            .setOrException(SubmittedStartDatePage(ResidentialAddrType), submittedStartDateDto)
            .setOrException(SubmittedInternationalAddressChoicePage, InternationalAddressChoiceDto.England)
        )
      )
      when(mockAddressSubmissionControllerHelper.isSubmittedAddressStartDateValid(any(), any())).thenReturn(
        true
      )
      when(
        mockAddressSubmissionControllerHelper
          .updateCitizenDetailsAddress(any(), any(), any(), any(), any())(any(), any(), any())
      ).thenReturn(
        Future.successful(Ok("Success"))
      )

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(fakePOSTRequest)

      status(result) mustBe 200
      contentAsString(result) mustNot include("Complete a P85 form (opens in new tab)")
    }

  }
}
