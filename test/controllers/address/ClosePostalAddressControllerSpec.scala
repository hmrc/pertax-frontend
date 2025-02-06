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
import controllers.bindable.PostalAddrType
import models._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => meq}
import play.api.http.Status.{BAD_REQUEST, OK, SEE_OTHER}
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testUtils.Fixtures
import testUtils.Fixtures.buildFakeAddress
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.model.DataEvent

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class ClosePostalAddressControllerSpec extends AddressBaseSpec {
  private lazy val controller: ClosePostalAddressController = app.injector.instanceOf[ClosePostalAddressController]
  val addressExceptionMessage                               = "Address does not exist in the current context"
  val expectedAddressConfirmationView: String               = updateAddressConfirmationView(
    PostalAddrType,
    closedPostalAddress = true,
    Some(fakeAddress.fullAddress),
    None,
    false
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
      val personDetails = PersonDetails(person, address, address)
      when(mockCitizenDetailsService.personDetails(any())(any(), any())).thenReturn(
        EitherT.rightT(personDetails)
      )

      def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

      val result: Future[Result] = controller.confirmSubmit(currentRequest)

      status(result) mustBe OK
      contentAsString(result) mustBe expectedAddressConfirmationView

      val arg       = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue

      pruneDataEvent(dataEvent) mustBe submitComparatorDataEvent(dataEvent, "closedAddressSubmitted")

      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), any())(any(), any())
      verify(controller.editAddressLockRepository, times(1)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "redirect to personal details if there is a lock on the correspondence address for the user" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails(person, None, address)
      when(mockCitizenDetailsService.personDetails(any())(any(), any())).thenReturn(
        EitherT.rightT(personDetails)
      )

      def getEditedAddressIndicators: List[AddressJourneyTTLModel] =
        List(AddressJourneyTTLModel("SomeNino", EditCorrespondenceAddress(Instant.now())))

      when(mockEditAddressLockRepository.get(any())).thenReturn(
        Future.successful(getEditedAddressIndicators)
      )

      val result: Future[Result] = controller.confirmSubmit(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.PersonalDetailsController.onPageLoad.url)

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockCitizenDetailsService, times(0)).updateAddress(meq(nino), meq("115"), any())(any(), any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "render the thank you page upon successful submission of closing the correspondence address and only a lock on the residential address" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails(person, address, address)
      when(mockCitizenDetailsService.personDetails(any())(any(), any())).thenReturn(
        EitherT.rightT(personDetails)
      )

      def currentRequest[A]: Request[A]                            = FakeRequest().asInstanceOf[Request[A]]
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

      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), any())(any(), any())
      verify(controller.editAddressLockRepository, times(1)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 400 if a BAD_REQUEST is received from citizen-details" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails(person, None, address)
      when(mockCitizenDetailsService.personDetails(any())(any(), any())).thenReturn(
        EitherT.rightT(personDetails)
      )

      def updateAddressResponse(): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
        EitherT[Future, UpstreamErrorResponse, HttpResponse](
          Future.successful(Left(UpstreamErrorResponse("", BAD_REQUEST)))
        )
      when(mockCitizenDetailsService.updateAddress(any(), any(), any())(any(), any())).thenReturn(
        updateAddressResponse()
      )

      val result: Future[Result] = controller.confirmSubmit()(FakeRequest())

      status(result) mustBe BAD_REQUEST
      verify(mockCitizenDetailsService, times(1))
        .updateAddress(meq(nino), meq("115"), any())(any(), any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if an unexpected error (418) is received from citizen-details" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails(person, None, address)
      when(mockCitizenDetailsService.personDetails(any())(any(), any())).thenReturn(
        EitherT.rightT(personDetails)
      )

      def updateAddressResponse(): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
        EitherT[Future, UpstreamErrorResponse, HttpResponse](
          Future.successful(Left(UpstreamErrorResponse("", IM_A_TEAPOT)))
        )
      when(mockCitizenDetailsService.updateAddress(any(), any(), any())(any(), any())).thenReturn(
        updateAddressResponse()
      )
      val result: Future[Result]                                                        = controller.confirmSubmit()(FakeRequest())

      status(result) mustBe INTERNAL_SERVER_ERROR
      verify(mockCitizenDetailsService, times(1))
        .updateAddress(meq(Fixtures.fakeNino), meq("115"), any())(any(), any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if a 5xx is received from citizen-details" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails(person, None, address)
      when(mockCitizenDetailsService.personDetails(any())(any(), any())).thenReturn(
        EitherT.rightT(personDetails)
      )

      def updateAddressResponse(): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
        EitherT[Future, UpstreamErrorResponse, HttpResponse](
          Future.successful(Left(UpstreamErrorResponse("", INTERNAL_SERVER_ERROR)))
        )
      when(mockCitizenDetailsService.updateAddress(any(), any(), any())(any(), any())).thenReturn(
        updateAddressResponse()
      )

      def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

      val result: Future[Result] = controller.confirmSubmit()(currentRequest)

      status(result) mustBe INTERNAL_SERVER_ERROR
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), any())(any(), any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if insert address lock fails" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails(person, None, address)
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(
        Future.successful(false)
      )
      when(mockCitizenDetailsService.personDetails(any())(any(), any())).thenReturn(
        EitherT.rightT(personDetails)
      )

      def currentRequest[A]: Request[A] = FakeRequest("POST", "/").asInstanceOf[Request[A]]

      val result: Future[Result] = controller.confirmSubmit(currentRequest)

      status(result) mustBe INTERNAL_SERVER_ERROR

      val arg       = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue

      pruneDataEvent(dataEvent) mustBe submitComparatorDataEvent(dataEvent, "closedAddressSubmitted")
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), any())(any(), any())
      verify(controller.editAddressLockRepository, times(1)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if fetching etag from citizen details fails" in {
      val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address
      val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails = PersonDetails(person, None, address)
      when(mockCitizenDetailsService.personDetails(any())(any(), any())).thenReturn(
        EitherT.rightT(personDetails)
      )

      when(mockCitizenDetailsService.getEtag(any())(any(), any())).thenReturn(
        EitherT.leftT(UpstreamErrorResponse("server error", INTERNAL_SERVER_ERROR))
      )

      def currentRequest[A]: Request[A] = FakeRequest("POST", "/test").asInstanceOf[Request[A]]

      val result: Future[Result] = controller.confirmSubmit(currentRequest)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

  }
}
