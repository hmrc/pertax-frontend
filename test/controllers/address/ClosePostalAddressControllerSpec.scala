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

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable.PostalAddrType
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.{AddressJourneyTTLModel, ETag, MovedToScotland, NonFilerSelfAssessmentUser}
import models.dto.{AddressDto, AddressPageVisitedDto}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.{BAD_REQUEST, OK, SEE_OTHER}
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsFormUrlEncoded, MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.EditAddressLockRepository
import services.{AddressMovedService, CitizenDetailsService, LocalSessionCache, PersonDetailsResponse, PersonDetailsSuccessResponse, UpdateAddressBadRequestResponse, UpdateAddressErrorResponse, UpdateAddressResponse, UpdateAddressSuccessResponse, UpdateAddressUnexpectedResponse}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.renderer.TemplateRenderer
import util.Fixtures.{buildFakeAddress, buildFakeAddressWithEndDate, buildPersonDetailsCorrespondenceAddress}
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec, Fixtures, LocalPartialRetriever}
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.{CloseCorrespondenceAddressChoiceView, ConfirmCloseCorrespondenceAddressView, UpdateAddressConfirmationView}

import scala.concurrent.{ExecutionContext, Future}

class ClosePostalAddressControllerSpec extends BaseSpec with MockitoSugar {

  trait LocalSetup {

    def asAddressDto(l: List[(String, String)]): AddressDto = AddressDto.ukForm.bind(l.toMap).get

    lazy val mockCitizenDetailsService: CitizenDetailsService = mock[CitizenDetailsService]
    lazy val mockEditAddressLockRepository: EditAddressLockRepository = mock[EditAddressLockRepository]
    lazy val mockAddressMovedService: AddressMovedService = mock[AddressMovedService]
    lazy val mockLocalSessionCache: LocalSessionCache = mock[LocalSessionCache]
    lazy val mockAuditConnector: AuditConnector = mock[AuditConnector]
    lazy val mockAuthJourney: AuthJourney = mock[AuthJourney]

    implicit lazy val ec: ExecutionContext = injected[ExecutionContext]

    def controller: ClosePostalAddressController =
      new ClosePostalAddressController(
        mockCitizenDetailsService,
        mockEditAddressLockRepository,
        mockAddressMovedService,
        new AddressJourneyCachingHelper(mockLocalSessionCache),
        mockAuditConnector,
        mockAuthJourney,
        injected[WithActiveTabAction],
        injected[MessagesControllerComponents],
        injected[CloseCorrespondenceAddressChoiceView],
        injected[ConfirmCloseCorrespondenceAddressView],
        injected[UpdateAddressConfirmationView],
        injected[DisplayAddressInterstitialView]
      )(injected[LocalPartialRetriever], injected[ConfigDecorator], injected[TemplateRenderer], ec)

    def pruneDataEvent(dataEvent: DataEvent): DataEvent =
      dataEvent.copy(
        tags = dataEvent.tags - "X-Request-Chain" - "X-Session-ID" - "token",
        detail = dataEvent.detail - "credId")

    def comparatorDataEvent(
      dataEvent: DataEvent,
      auditType: String,
      uprn: Option[String],
      includeOriginals: Boolean,
      submittedLine1: Option[String] = Some("1 Fake Street"),
      addressType: Option[String] = Some("Residential")) = DataEvent(
      "pertax-frontend",
      auditType,
      dataEvent.eventId,
      Map("path" -> "/test", "transactionName" -> "change_of_address"),
      Map(
        "nino"              -> Some(Fixtures.fakeNino.nino),
        "etag"              -> Some("115"),
        "submittedLine1"    -> submittedLine1,
        "submittedLine2"    -> Some("Fake Town"),
        "submittedLine3"    -> Some("Fake City"),
        "submittedLine4"    -> Some("Fake Region"),
        "submittedPostcode" -> Some("AA1 1AA"),
        "submittedCountry"  -> None,
        "addressType"       -> addressType,
        "submittedUPRN"     -> uprn,
        "originalLine1"     -> Some("1 Fake Street").filter(x => includeOriginals),
        "originalLine2"     -> Some("Fake Town").filter(x => includeOriginals),
        "originalLine3"     -> Some("Fake City").filter(x => includeOriginals),
        "originalLine4"     -> Some("Fake Region").filter(x => includeOriginals),
        "originalPostcode"  -> Some("AA1 1AA").filter(x => includeOriginals),
        "originalCountry"   -> Some("Country(UK,United Kingdom)").filter(x => includeOriginals),
        "originalUPRN"      -> uprn.filter(x => includeOriginals)
      ).map(t => t._2.map((t._1, _))).flatten.toMap,
      dataEvent.generatedAt
    )

    lazy val nino = Fixtures.fakeNino

    lazy val fakePersonDetails = Fixtures.buildPersonDetails

    def personDetailsResponse: PersonDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)

    def sessionCacheResponse: Option[CacheMap] =
      Some(CacheMap("id", Map("addressLookupServiceDown" -> Json.toJson(Some(true)))))

    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

    def isInsertCorrespondenceAddressLockSuccessful: Boolean = true

    def getEditedAddressIndicators: List[AddressJourneyTTLModel] = List.empty

    def updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse

    def eTagResponse: Option[ETag] = Some(ETag("115"))

    when(mockAuditConnector.sendEvent(any())(any(), any())) thenReturn {
      Future.successful(AuditResult.Success)
    }
    when(mockCitizenDetailsService.personDetails(meq(nino))(any())) thenReturn {
      Future.successful(personDetailsResponse)
    }
    when(mockCitizenDetailsService.getEtag(any())(any())) thenReturn {
      Future.successful(eTagResponse)
    }
    when(mockCitizenDetailsService.updateAddress(any(), any(), any())(any())) thenReturn {
      Future.successful(updateAddressResponse)
    }
    when(mockCitizenDetailsService.updateAddress(any(), any(), any())(any())) thenReturn {
      Future.successful(updateAddressResponse)
    }
    when(mockLocalSessionCache.cache(any(), any())(any(), any(), any())) thenReturn {
      Future.successful(CacheMap("id", Map.empty))
    }
    when(mockLocalSessionCache.fetch()(any(), any())).thenReturn {
      Future.successful(sessionCacheResponse)
    }
    when(mockLocalSessionCache.remove()(any(), any())) thenReturn {
      Future.successful(mock[HttpResponse])
    }
    when(mockEditAddressLockRepository.insert(any(), any())) thenReturn {
      Future.successful(isInsertCorrespondenceAddressLockSuccessful)
    }
    when(mockEditAddressLockRepository.get(any())) thenReturn {
      Future.successful(getEditedAddressIndicators)
    }
    when(mockAddressMovedService.moved(any[String](), any[String]())(any(), any())) thenReturn {
      Future.successful(MovedToScotland)
    }

    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(
            request = currentRequest[A],
            personDetails = Some(buildPersonDetailsCorrespondenceAddress),
            saUser = NonFilerSelfAssessmentUser
          ).asInstanceOf[UserRequest[A]]
        )
    })
  }

  "Calling AddressController.onPageLoad" should {

    "display the closeCorrespondenceAddressChoice form that contains the main address" in new LocalSetup {
      val result = controller.onPageLoad(FakeRequest())

      contentAsString(result) should include(buildFakeAddress.line1.getOrElse("line6"))

      status(result) shouldBe OK
    }
  }

  "Calling AddressController.onSubmit" should {

    "redirect to expected confirm close correspondence confirmation page when supplied with value = Yes (true)" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("onPageLoad" -> "true")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/close-correspondence-address-confirm")
    }

    "redirect to personal details page when supplied with value = No (false)" in new LocalSetup {
      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("onPageLoad" -> "false")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
    }

    "return a bad request when supplied no value" in new LocalSetup {
      val result = controller.onSubmit(FakeRequest())

      status(result) shouldBe BAD_REQUEST
    }
  }

  "Calling AddressController.confirmPageLoad" should {

    "return OK when confirmPageLoad is called" in new LocalSetup {
      val result = controller.confirmPageLoad(FakeRequest())
      status(result) shouldBe OK
    }
  }

  "Calling AddressController.confirmSubmit" should {

    def submitComparatorDataEvent(dataEvent: DataEvent, auditType: String, uprn: Option[String]) = DataEvent(
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

    "render the thank you page upon successful submission of closing the correspondence address" in new LocalSetup {

      override def currentRequest[A]: Request[A] = FakeRequest("POST", "/test").asInstanceOf[Request[A]]

      val result = controller.confirmSubmit(FakeRequest())

      status(result) shouldBe OK

      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue

      pruneDataEvent(dataEvent) shouldBe submitComparatorDataEvent(dataEvent, "closedAddressSubmitted", Some("GB101"))

      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), any())(any())
      verify(controller.editAddressLockRepository, times(1)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "redirect to personal details if there is a lock on the correspondence address for the user" in new LocalSetup {
      override def getEditedAddressIndicators: List[AddressJourneyTTLModel] =
        List(mock[AddressJourneyTTLModel])

      val result = controller.confirmSubmit(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.PersonalDetailsController.onPageLoad().url)

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockCitizenDetailsService, times(0)).updateAddress(meq(nino), meq("115"), any())(any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 400 if UpdateAddressBadRequestResponse is received from citizen-details" in new LocalSetup {
      override def updateAddressResponse: UpdateAddressResponse = UpdateAddressBadRequestResponse

      val result = controller.confirmSubmit()(FakeRequest())

      status(result) shouldBe BAD_REQUEST
      verify(mockCitizenDetailsService, times(1))
        .updateAddress(meq(nino), meq("115"), any())(any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if an UpdateAddressUnexpectedResponse is received from citizen-details" in new LocalSetup {
      override lazy val updateAddressResponse = UpdateAddressUnexpectedResponse(HttpResponse(SEE_OTHER))

      val result = controller.confirmSubmit()(FakeRequest())

      status(result) shouldBe INTERNAL_SERVER_ERROR
      verify(mockCitizenDetailsService, times(1))
        .updateAddress(meq(Fixtures.fakeNino), meq("115"), any())(any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if an UpdateAddressErrorResponse is received from citizen-details" in new LocalSetup {
      override lazy val updateAddressResponse = UpdateAddressErrorResponse(new RuntimeException("Any exception"))

      override def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

      val result = controller.confirmSubmit()(FakeRequest())

      status(result) shouldBe INTERNAL_SERVER_ERROR
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), any())(any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if insert address lock fails" in new LocalSetup {
      override def isInsertCorrespondenceAddressLockSuccessful: Boolean = false

      override def currentRequest[A]: Request[A] = FakeRequest("POST", "/test").asInstanceOf[Request[A]]

      val result = controller.confirmSubmit(currentRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR

      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue

      pruneDataEvent(dataEvent) shouldBe submitComparatorDataEvent(dataEvent, "closedAddressSubmitted", Some("GB101"))
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), any())(any())
      verify(controller.editAddressLockRepository, times(1)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if fetching etag from citizen details fails" in new LocalSetup {
      override def eTagResponse: Option[ETag] = None

      override def currentRequest[A]: Request[A] = FakeRequest("POST", "/test").asInstanceOf[Request[A]]

      val result = controller.confirmSubmit(currentRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
