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
import models.{NonFilerSelfAssessmentUser, PersonDetails, UserAnswers}
import models.addresslookup.{Address, AddressRecord, Country}
import models.dto.{AddressDto, AddressPageVisitedDto}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, OK, SEE_OTHER}
import play.api.inject.bind
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.JourneyCacheRepository
import routePages.{AddressLookupServiceDownPage, HasAddressAlreadyVisitedPage, SelectedAddressRecordPage, SubmittedAddressPage}
import services.CitizenDetailsService
import testUtils.BaseSpec
import testUtils.Fixtures.{buildPersonDetailsWithPersonalAndCorrespondenceAddress, fakeStreetPafAddressRecord, fakeStreetTupleListAddressForUnmodified, fakeStreetTupleListInternationalAddress}
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.{ExecutionContext, Future}

class UpdateInternationalAddressControllerSpec extends BaseSpec {
  def asAddressDto(l: List[(String, String)]): AddressDto = AddressDto.ukForm.bind(l.toMap).get
  val personDetails: PersonDetails                        = buildPersonDetailsWithPersonalAndCorrespondenceAddress

  val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]
  val mockCitizenDetailsService: CitizenDetailsService   = mock[CitizenDetailsService]
  val mockAuditConnector: AuditConnector                 = mock[AuditConnector]

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
      bind[AuditConnector].toInstance(mockAuditConnector)
    )
    .build()

  def currentRequest[A]: Request[A] = FakeRequest("GET", "/test").asInstanceOf[Request[A]]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockJourneyCacheRepository)
    reset(mockCitizenDetailsService)
    reset(mockAuditConnector)

    when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
      EitherT[Future, UpstreamErrorResponse, PersonDetails](
        Future.successful(Right(personDetails))
      )
    )

  }

  private lazy val controller: UpdateInternationalAddressController =
    app.injector.instanceOf[UpdateInternationalAddressController]

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

    "fetch the selected address and a residential residencyChoice has been selected from the session cache and return 200" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(SelectedAddressRecordPage(ResidentialAddrType), fakeStreetPafAddressRecord)
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
    }

    "find no selected address with residential address type but residencyChoice in the session cache and still return 200" in {

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
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        .setOrException(SelectedAddressRecordPage(PostalAddrType), fakeStreetPafAddressRecord)

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

    "verify an audit event has been sent when user chooses to add/amend view address" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
    }

    "verify an audit event has been sent when user chooses to add postal address" in {

      val userAnswers: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
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

    "return 303, caching addressDto and redirecting to review changes page when supplied valid form input on a postal journey and input default startDate into cache" in {

      val userAnswers: UserAnswers = UserAnswers.empty("id").setOrException(AddressLookupServiceDownPage, true)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody(fakeStreetTupleListInternationalAddress: _*)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/changes")
    }

    "return 303, caching addressDto and redirecting to enter start date page when supplied valid form input on a non postal journey" in {

      val userAnswers: UserAnswers = UserAnswers.empty("id").setOrException(AddressLookupServiceDownPage, true)

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody(fakeStreetTupleListInternationalAddress: _*)
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/enter-start-date")
    }
  }
}
