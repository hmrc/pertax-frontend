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
import cats.implicits._
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.bindable.PostalAddrType
import models._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, OK, SEE_OTHER}
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.inject.bind
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.CitizenDetailsService
import testUtils.Fixtures.{buildFakeAddress, buildPersonDetailsWithPersonalAndCorrespondenceAddress}
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{SessionKeys, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.audit.model.DataEvent
import views.html.personaldetails.UpdateAddressConfirmationView

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class ClosePostalAddressControllerSpec extends BaseSpec {
  val personDetails: PersonDetails = buildPersonDetailsWithPersonalAndCorrespondenceAddress
  val nino: Nino                   = buildPersonDetailsWithPersonalAndCorrespondenceAddress.person.nino.get

  val mockCitizenDetailsService: CitizenDetailsService = mock[CitizenDetailsService]
  val mockAuditConnector: AuditConnector               = mock[AuditConnector]

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
      bind[CitizenDetailsService].toInstance(mockCitizenDetailsService),
      bind[AuditConnector].toInstance(mockAuditConnector)
    )
    .build()

  def currentRequest[A]: Request[A] = FakeRequest("GET", "/test").asInstanceOf[Request[A]]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockCitizenDetailsService)
    reset(mockAuditConnector)

    when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
      EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
        Future.successful(Right(Some(personDetails)))
      )
    )

  }

  private lazy val controller: ClosePostalAddressController = app.injector.instanceOf[ClosePostalAddressController]

  def pruneDataEvent(dataEvent: DataEvent): DataEvent              =
    dataEvent
      .copy(tags = dataEvent.tags - "X-Request-Chain" - "X-Session-ID" - "token", detail = dataEvent.detail - "credId")
  val updateAddressConfirmationView: UpdateAddressConfirmationView =
    app.injector.instanceOf[UpdateAddressConfirmationView]
  val fakeAddress: Address                                         = buildFakeAddress
  lazy val messages: Messages                                      = MessagesImpl(Lang("en"), messagesApi)
  val addressExceptionMessage                                      = "Address does not exist in the current context"
  lazy val expectedAddressConfirmationView: String                 = updateAddressConfirmationView(
    PostalAddrType,
    closedPostalAddress = true,
    Some(fakeAddress.fullAddress),
    None,
    displayP85Message = false
  )(
    buildUserRequest(request = FakeRequest(), saUser = NonFilerSelfAssessmentUser),
    messages
  ).toString

  "onPageLoad" must {

    "display the closeCorrespondenceAddressChoice form that contains the view address" in {
      val result: Future[Result] = controller.onPageLoad(FakeRequest())

      contentAsString(result) must include(buildFakeAddress.line1.getOrElse("line6"))

      status(result) mustBe OK
    }

  }

  "onSubmit" must {
    "redirect to expected confirm close correspondence confirmation page when supplied with value = Yes (true)" in {
      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("onPageLoad" -> "true")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/close-correspondence-address-confirm")
    }

    "redirect to personal details page when supplied with value = No (false)" in {
      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("onPageLoad" -> "false")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
    }

    "return a bad request when supplied no value" in {
      val result: Future[Result] = controller.onSubmit(FakeRequest())

      status(result) mustBe BAD_REQUEST
    }

  }

  "confirmPageLoad" should {

    "return OK when confirmPageLoad is called" in {
      val result: Future[Result] = controller.confirmPageLoad(FakeRequest())
      status(result) mustBe OK
    }

  }

  "confirmSubmit" should {

    def submitComparatorDataEvent(dataEvent: DataEvent, auditType: String): DataEvent = DataEvent(
      "pertax-frontend",
      auditType,
      dataEvent.eventId,
      Map("path" -> "/", "transactionName" -> "closure_of_correspondence"),
      Map(
        "nino"              -> Some(Fixtures.fakeNino.nino),
        "etag"              -> Some("115"),
        "submittedLine1"    -> Some("1 Fake Street"),
        "submittedLine2"    -> Some("Fake Town"),
        "submittedLine3"    -> Some("Fake City"),
        "submittedLine4"    -> Some("Fake Region"),
        "submittedPostcode" -> Some("AA1 1AA"),
        "submittedCountry"  -> None,
        "addressType"       -> Some("correspondence")
      ).collect { case (k, Some(v)) => k -> v },
      dataEvent.generatedAt
    )

    "render the thank you page upon successful submission of closing the correspondence address and no locks present" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails("115", person, address, address)
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails))
      )
      when(mockCitizenDetailsService.clearCachedPersonDetails(any())(any())).thenReturn(Future.successful(()))

      when(mockEditAddressLockRepository.get(any())).thenReturn(
        Future.successful(List.empty)
      )
      when(mockCitizenDetailsService.updateAddress(any(), any(), any(), any())(any(), any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](true)
      )
      when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(Success))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))

      def currentRequest[A]: Request[A] =
        FakeRequest().withSession(SessionKeys.sessionId -> "1").asInstanceOf[Request[A]]

      val result: Future[Result] = controller.confirmSubmit(currentRequest)

      status(result) mustBe OK
      contentAsString(result) mustBe expectedAddressConfirmationView

      val arg                  = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent: DataEvent = arg.getValue

      pruneDataEvent(dataEvent) mustBe submitComparatorDataEvent(dataEvent, "closedAddressSubmitted")

      verify(mockCitizenDetailsService, times(1))
        .updateAddress(meq(nino), any(), meq(personDetails), any())(any(), any(), any())
      verify(controller.editAddressLockRepository, times(1)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "redirect to personal details if there is a lock on the correspondence address for the user" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails("115", person, None, address)
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](
          Some(personDetails.copy(address = personDetails.correspondenceAddress.map(_.copy(isRls = true))))
        )
      )

      def getEditedAddressIndicators: List[AddressJourneyTTLModel] =
        List(AddressJourneyTTLModel("SomeNino", EditCorrespondenceAddress(Instant.now())))

      when(mockEditAddressLockRepository.get(any())).thenReturn(
        Future.successful(getEditedAddressIndicators)
      )
      when(mockCitizenDetailsService.clearCachedPersonDetails(any())(any())).thenReturn(Future.successful(()))

      when(mockCitizenDetailsService.updateAddress(any(), any(), any(), any())(any(), any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](true)
      )
      when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(Success))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))

      val result: Future[Result] = controller.confirmSubmit(FakeRequest().withSession(SessionKeys.sessionId -> "1"))

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.PersonalDetailsController.onPageLoad.url)

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockCitizenDetailsService, times(0))
        .updateAddress(meq(nino), any(), meq(personDetails), any())(any(), any(), any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "render the thank you page upon successful submission of closing the correspondence address and only a lock on the residential address" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails("115", person, address, address)
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails))
      )
      when(mockCitizenDetailsService.clearCachedPersonDetails(any())(any())).thenReturn(Future.successful(()))

      when(mockEditAddressLockRepository.get(any())).thenReturn(
        Future.successful(List.empty)
      )
      when(mockCitizenDetailsService.updateAddress(any(), any(), any(), any())(any(), any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](true)
      )
      when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(Success))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))

      def currentRequest[A]: Request[A]                            =
        FakeRequest().withSession(SessionKeys.sessionId -> "1").asInstanceOf[Request[A]]
      def getEditedAddressIndicators: List[AddressJourneyTTLModel] =
        List(AddressJourneyTTLModel("SomeNino", EditResidentialAddress(Instant.now())))
      when(mockEditAddressLockRepository.get(any())).thenReturn(
        Future.successful(getEditedAddressIndicators)
      )
      val result: Future[Result]                                   = controller.confirmSubmit(currentRequest)

      status(result) mustBe OK
      contentAsString(result) mustBe expectedAddressConfirmationView

      val arg       = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue

      pruneDataEvent(dataEvent) mustBe submitComparatorDataEvent(dataEvent, "closedAddressSubmitted")

      verify(mockCitizenDetailsService, times(1))
        .updateAddress(meq(nino), any(), meq(personDetails), any())(any(), any(), any())
      verify(controller.editAddressLockRepository, times(1)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if a BAD_REQUEST is received from citizen-details" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails("115", person, None, address)
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails))
      )
      when(mockCitizenDetailsService.clearCachedPersonDetails(any())(any())).thenReturn(Future.successful(()))

      when(mockEditAddressLockRepository.get(any())).thenReturn(
        Future.successful(List.empty)
      )
      when(mockCitizenDetailsService.updateAddress(any(), any(), any(), any())(any(), any(), any())).thenReturn(
        EitherT.leftT[Future, PersonDetails](UpstreamErrorResponse("bad request", BAD_REQUEST))
      )

      val result: Future[Result] = controller.confirmSubmit()(FakeRequest())

      status(result) mustBe INTERNAL_SERVER_ERROR
      verify(mockCitizenDetailsService, times(1))
        .updateAddress(meq(nino), any(), meq(personDetails), any())(any(), any(), any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if an unexpected error (418) is received from citizen-details" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails("115", person, None, address)
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails))
      )
      when(mockCitizenDetailsService.clearCachedPersonDetails(any())(any())).thenReturn(Future.successful(()))

      when(mockEditAddressLockRepository.get(any())).thenReturn(
        Future.successful(List.empty)
      )
      when(mockCitizenDetailsService.updateAddress(any(), any(), any(), any())(any(), any(), any())).thenReturn(
        EitherT.leftT[Future, Boolean](UpstreamErrorResponse("unexpected error", 418))
      )

      val result: Future[Result] = controller.confirmSubmit()(FakeRequest())

      status(result) mustBe INTERNAL_SERVER_ERROR
      verify(mockCitizenDetailsService, times(1))
        .updateAddress(meq(Fixtures.fakeNino), any(), meq(personDetails), any())(any(), any(), any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if a 5xx is received from citizen-details" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails("115", person, None, address)
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails))
      )
      when(mockCitizenDetailsService.clearCachedPersonDetails(any())(any())).thenReturn(Future.successful(()))

      when(mockEditAddressLockRepository.get(any())).thenReturn(
        Future.successful(List.empty)
      )
      when(mockCitizenDetailsService.updateAddress(any(), any(), any(), any())(any(), any(), any())).thenReturn(
        EitherT.leftT[Future, Boolean](UpstreamErrorResponse("server error", INTERNAL_SERVER_ERROR))
      )

      def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

      val result: Future[Result] = controller.confirmSubmit()(currentRequest)

      status(result) mustBe INTERNAL_SERVER_ERROR
      verify(mockCitizenDetailsService, times(1))
        .updateAddress(meq(nino), any(), meq(personDetails), any())(any(), any(), any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "render the error view when citizen-details returns Etag error" in {
      val etagErrorResponse              =
        "The remote endpoint has indicated that Optimistic Lock value is not correct."
      val etagErrorUpstreamErrorResponse =
        s"""POST of 'https://citizen-details.protected.mdtp:443/citizen-details/<nino>/designatory-details/address' returned 400. Response body: '{"reason":"$etagErrorResponse"}'"""

      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails("115", person, None, address)
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))
      when(mockCitizenDetailsService.clearCachedPersonDetails(any())(any())).thenReturn(Future.successful(()))
      when(mockCitizenDetailsService.updateAddress(any(), any(), any(), any())(any(), any(), any()))
        .thenReturn(
          EitherT.leftT[Future, Option[PersonDetails]](
            UpstreamErrorResponse(etagErrorUpstreamErrorResponse, BAD_REQUEST)
          )
        )

      when(mockEditAddressLockRepository.get(any())).thenReturn(
        Future.successful(List.empty)
      )

      val result: Future[Result] = controller.confirmSubmit(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/problem-with-service")
    }

    "render the error view when citizen-details returns Etag conflict" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails("115", person, None, address)
      when(mockCitizenDetailsService.personDetails(any(), any())(any(), any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(personDetails)))
      when(mockCitizenDetailsService.clearCachedPersonDetails(any())(any())).thenReturn(Future.successful(()))
      when(mockCitizenDetailsService.updateAddress(any(), any(), any(), any())(any(), any(), any()))
        .thenReturn(
          EitherT.leftT[Future, Option[PersonDetails]](
            UpstreamErrorResponse("", CONFLICT)
          )
        )

      when(mockEditAddressLockRepository.get(any())).thenReturn(
        Future.successful(List.empty)
      )

      val result: Future[Result] = controller.confirmSubmit(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/problem-with-service")
    }
  }
}
