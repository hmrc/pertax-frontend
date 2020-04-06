/*
 * Copyright 2020 HM Revenue & Customs
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

import controllers.auth.requests.UserRequest
import controllers.bindable.PostalAddrType
import models.{AddressJourneyTTLModel, AnyOtherMove, EditSoleAddress, NonFilerSelfAssessmentUser}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito.{times, verify, when}
import play.api.libs.json.Json
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import reactivemongo.bson.BSONDateTime
import services.{UpdateAddressBadRequestResponse, UpdateAddressErrorResponse, UpdateAddressResponse, UpdateAddressSuccessResponse, UpdateAddressUnexpectedResponse}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.audit.model.DataEvent
import util.Fixtures.{buildFakeAddress, buildFakeAddressWithEndDate, buildPersonDetailsCorrespondenceAddress}
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, Fixtures}

import scala.concurrent.Future

class ClosePostalAddressControllerSpec extends AddressSpecHelper {

  trait LocalSetup {

    val requestWithForm: Request[_] = FakeRequest()

    val sessionCacheResponse: Option[CacheMap] =
      Some(CacheMap("id", Map("addressLookupServiceDown" -> Json.toJson(Some(true)))))

    val authActionResult: ActionBuilderFixture = new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(
            saUser = NonFilerSelfAssessmentUser,
            personDetails = Some(buildPersonDetailsCorrespondenceAddress),
            request = requestWithForm.asInstanceOf[Request[A]]
          )
        )
    }

    val repoGetResult: List[AddressJourneyTTLModel] = List.empty

    val repoUpdatedResult: Boolean = true

    val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse

    def controller =
      new ClosePostalAddressController(
        mockCitizenDetailsService,
        mockAddressMovedService,
        mockEditAddressLockRepository,
        mockAuditConnector,
        mockLocalSessionCache,
        mockAuthJourney,
        withActiveTabAction,
        mcc
      ) {

        when(mockAuthJourney.authWithPersonalDetails) thenReturn
          authActionResult

        when(mockLocalSessionCache.fetch()(any(), any())) thenReturn
          sessionCacheResponse

        when(mockLocalSessionCache.cache(any(), any())(any(), any(), any())) thenReturn
          Future.successful(CacheMap("", Map.empty))

        when(mockLocalSessionCache.remove()(any(), any())) thenReturn
          Future.successful(HttpResponse(200))

        when(mockCitizenDetailsService.updateAddress(any(), any(), any())(any())) thenReturn
          Future.successful(updateAddressResponse)

        when(mockEditAddressLockRepository.insert(any(), any())) thenReturn
          Future.successful(repoUpdatedResult)

        when(mockEditAddressLockRepository.get(any())) thenReturn
          Future.successful(repoGetResult)

        when(mockAddressMovedService.moved(any(), any())(any(), any())) thenReturn
          Future.successful(AnyOtherMove)

        when(mockAuditConnector.sendEvent(any())(any(), any())) thenReturn
          Future.successful(Success)
      }
  }

  "onPageLoad" should {

    "display the closeCorrespondenceAddressChoice form that contains the main address" in new LocalSetup {
      val result = controller.onPageLoad(FakeRequest())

      contentAsString(result) should include(buildFakeAddress.line1.getOrElse("line6"))

      status(result) shouldBe OK
    }
  }

  "onSubmit" should {

    "return 400 when supplied invalid form input" in new LocalSetup {

      val result = controller.onSubmit(FakeRequest("POST", ""))

      status(result) shouldBe BAD_REQUEST
    }

    "return 303, caching closePostalAddressChoiceDto and redirecting to review changes page when supplied valid form input" in new LocalSetup {

      override val requestWithForm =
        FakeRequest("POST", "").withFormUrlEncodedBody("closePostalAddressChoice" -> "true")

      val result = controller.onSubmit()(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/close-correspondence-address-confirm")
    }

    "return 303, caching closePostalAddressChoiceDto and redirecting to personal details page" in new LocalSetup {

      override val requestWithForm =
        FakeRequest("POST", "").withFormUrlEncodedBody("closePostalAddressChoice" -> "false")

      val result = controller.onSubmit(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
    }
  }

  "onPageLoadConfirm" should {

    "render the appropriate content that includes the address" in new LocalSetup {
      val result = controller.onPageLoadConfirm(FakeRequest())

      contentAsString(result) should include(buildFakeAddress.line1.getOrElse("line6"))
    }
  }

  "Calling AddressController.submitConfirmClosePostalAddress" should {

    val fakeAddress = buildFakeAddressWithEndDate

    "render the thank you page upon successful submission of closing the correspondence address" in new LocalSetup {
      override val authActionResult = new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              personDetails = Some(buildPersonDetailsCorrespondenceAddress),
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]]
            )
          )
      }

      val result = controller.onSubmitConfirm(FakeRequest())

      status(result) shouldBe OK

      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue

      pruneDataEvent(dataEvent) shouldBe comparatorDataEvent(dataEvent, "closedAddressSubmitted", Some("GB101"))
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
      verify(controller.editAddressLockRepository, times(1)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "redirect to personal details if there is a lock on the correspondence address for the user" in new LocalSetup {

      override val repoGetResult: List[AddressJourneyTTLModel] =
        List(AddressJourneyTTLModel(nino.nino, EditSoleAddress(BSONDateTime(1000000000.toLong))))

      override val authActionResult: ActionBuilderFixture = new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm, personDetails = Some(buildPersonDetailsCorrespondenceAddress))
              .asInstanceOf[UserRequest[A]]
          )
      }

      val result = controller.onSubmitConfirm(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.PersonalDetailsController.onPageLoad().url)

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockCitizenDetailsService, times(0)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 400 if UpdateAddressBadRequestResponse is received from citizen-details" in new LocalSetup {
      override val updateAddressResponse = UpdateAddressBadRequestResponse

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request, personDetails = Some(buildPersonDetailsCorrespondenceAddress))
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.onSubmitConfirm()(FakeRequest())

      status(result) shouldBe BAD_REQUEST
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if an UpdateAddressUnexpectedResponse is received from citizen-details" in new LocalSetup {
      override val updateAddressResponse = UpdateAddressUnexpectedResponse(HttpResponse(SEE_OTHER))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request, personDetails = Some(buildPersonDetailsCorrespondenceAddress))
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.onSubmitConfirm()(FakeRequest())

      status(result) shouldBe INTERNAL_SERVER_ERROR
      verify(mockCitizenDetailsService, times(1))
        .updateAddress(meq(Fixtures.fakeNino), meq("115"), meq(fakeAddress))(any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if an UpdateAddressErrorResponse is received from citizen-details" in new LocalSetup {
      override val updateAddressResponse = UpdateAddressErrorResponse(new RuntimeException("Any exception"))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request, personDetails = Some(buildPersonDetailsCorrespondenceAddress))
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.onSubmitConfirm()(FakeRequest())

      status(result) shouldBe INTERNAL_SERVER_ERROR
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if insert address lock fails" in new LocalSetup {
      override val repoUpdatedResult = false

      override val authActionResult = new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              personDetails = Some(buildPersonDetailsCorrespondenceAddress),
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]]
            )
          )
      }

      val result = controller.onSubmitConfirm(FakeRequest())

      status(result) shouldBe INTERNAL_SERVER_ERROR

      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue

      pruneDataEvent(dataEvent) shouldBe comparatorDataEvent(dataEvent, "closedAddressSubmitted", Some("GB101"))
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
      verify(controller.editAddressLockRepository, times(1)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }
  }

  def comparatorDataEvent(dataEvent: DataEvent, auditType: String, uprn: Option[String]) = DataEvent(
    "pertax-frontend",
    auditType,
    dataEvent.eventId,
    Map("path" -> "/test", "transactionName" -> "closure_of_correspondence"),
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

  def pruneDataEvent(dataEvent: DataEvent): DataEvent =
    dataEvent
      .copy(tags = dataEvent.tags - "X-Request-Chain" - "X-Session-ID" - "token", detail = dataEvent.detail - "credId")
}
