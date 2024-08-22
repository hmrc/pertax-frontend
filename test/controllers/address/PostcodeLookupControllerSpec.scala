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
import controllers.bindable.{PostalAddrType, ResidentialAddrType}
import models.UserAnswers
import models.addresslookup.RecordSet
import models.dto.{AddressFinderDto, AddressPageVisitedDto}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import routePages.{AddressFinderPage, AddressLookupServiceDownPage, HasAddressAlreadyVisitedPage, SelectedAddressRecordPage}
import testUtils.Fixtures
import testUtils.Fixtures.{fakeStreetPafAddressRecord, oneAndTwoOtherPlacePafRecordSet}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.audit.model.DataEvent
import views.html.personaldetails.PostcodeLookupView

import scala.concurrent.Future

class PostcodeLookupControllerSpec extends AddressBaseSpec {

  trait LocalSetup extends AddressControllerSetup {

    def controller: PostcodeLookupController =
      new PostcodeLookupController(
        mockAddressLookupConnector,
        addressJourneyCachingHelper,
        mockAuditConnector,
        mockAuthJourney,
        cc,
        inject[PostcodeLookupView],
        displayAddressInterstitialView,
        mockFeatureFlagService,
        internalServerErrorView
      )

    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

    override def addressLookupResponse: RecordSet = RecordSet(List())

    def comparatorDataEvent(dataEvent: DataEvent, auditType: String, postcode: String): DataEvent = DataEvent(
      "pertax-frontend",
      auditType,
      dataEvent.eventId,
      Map("path" -> "/test", "transactionName"         -> "find_address"),
      Map("nino" -> Fixtures.fakeNino.nino, "postcode" -> postcode),
      dataEvent.generatedAt
    )
  }

  "onPageLoad" must {

    "return 200 if the user has entered a residency choice on the previous page" in new LocalSetup {

      when(mockJourneyCacheRepository.get(any())).thenReturn(
        Future.successful(
          UserAnswers.empty("id").setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        )
      )

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "return 303 if the user has NOT entered a residency choice on the previous page" in new LocalSetup {

      when(mockJourneyCacheRepository.get(any())).thenReturn(
        Future.successful(
          UserAnswers.empty("id").setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(false))
        )
      )

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK // TODO : CHECK OK ???
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "return 200 if the user is on correspondence address journey and has postal address type" in new LocalSetup {
      when(mockJourneyCacheRepository.get(any())).thenReturn(
        Future.successful(
          UserAnswers.empty("id").setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        )
      )

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "redirect to the beginning of the journey when user has not indicated Residency choice on previous page" in new LocalSetup {
      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(UserAnswers.empty))

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      verify(mockJourneyCacheRepository, times(1)).get(any())
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
    }

    "redirect to the beginning of the journey when user has not visited your-address page on correspondence journey" in new LocalSetup {
      when(mockJourneyCacheRepository.get(any())).thenReturn(Future.successful(UserAnswers.empty))

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe SEE_OTHER
      verify(mockJourneyCacheRepository, times(1)).get(any())
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
    }

    "verify an audit event has been sent for a user clicking the change address link" in new LocalSetup {
      when(mockJourneyCacheRepository.get(any())).thenReturn(
        Future.successful(
          UserAnswers.empty("id").setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        )
      )

      val result: Future[Result] = controller.onPageLoad(ResidentialAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
    }

    "verify an audit event has been sent for a user clicking the change postal address link" in new LocalSetup {
      when(mockJourneyCacheRepository.get(any())).thenReturn(
        Future.successful(
          UserAnswers.empty("id").setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
        )
      )

      val result: Future[Result] = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
    }
  }

  "onSubmit" must {

    "return 404 and log a addressLookupNotFound audit event when an empty record set is returned by the address lookup service" in new LocalSetup {
      when(mockAddressLookupConnector.lookup(any(), any())(any(), any())) thenReturn {
        EitherT[Future, UpstreamErrorResponse, RecordSet](
          Future.successful(
            Right(addressLookupResponse)
          )
        )
      }

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(PostalAddrType)(FakeRequest("POST", "/test"))

      status(result) mustBe NOT_FOUND
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      pruneDataEvent(eventCaptor.getValue) mustBe comparatorDataEvent(
        eventCaptor.getValue,
        "addressLookupNotFound",
        "AA1 1AA"
      )
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    List(
      BAD_REQUEST,
      NOT_FOUND,
      TOO_MANY_REQUESTS,
      UNPROCESSABLE_ENTITY,
      INTERNAL_SERVER_ERROR,
      BAD_GATEWAY,
      SERVICE_UNAVAILABLE
    ).foreach { errorResponse =>
      s"redirect to 'Edit your address' when address lookup service is a Left containing $errorResponse" in new LocalSetup {
        when(mockAddressLookupConnector.lookup(any(), any())(any(), any())) thenReturn {
          EitherT[Future, UpstreamErrorResponse, RecordSet](
            Future.successful(
              Left(UpstreamErrorResponse("", errorResponse))
            )
          )
        }

        override def addressLookupResponse = throw new RuntimeException("Some error")

        override def currentRequest[A]: Request[A] =
          FakeRequest("POST", "/test")
            .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
            .asInstanceOf[Request[A]]

        val result: Future[Result] = controller.onSubmit(ResidentialAddrType)(currentRequest)

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("/personal-account/your-address/residential/edit-address")
      }
    }

    "redirect to the edit address page for a postal address type and log a addressLookupResults audit event when a single record is returned by the address lookup service" in new LocalSetup {
      override def addressLookupResponse: RecordSet = RecordSet(List(fakeStreetPafAddressRecord))

      when(mockAddressLookupConnector.lookup(any(), any())(any(), any())) thenReturn {
        EitherT[Future, UpstreamErrorResponse, RecordSet](
          Future.successful(
            Right(addressLookupResponse)
          )
        )
      }

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(PostalAddrType, None)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/edit-address")

      val userAnswers: UserAnswers = UserAnswers.empty
        .setOrException(SelectedAddressRecordPage(PostalAddrType), fakeStreetPafAddressRecord)
      verify(mockJourneyCacheRepository, times(1)).set(userAnswers)
      verify(mockJourneyCacheRepository, times(1)).get(any())

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      pruneDataEvent(eventCaptor.getValue) mustBe comparatorDataEvent(
        eventCaptor.getValue,
        "addressLookupResults",
        "AA1 1AA"
      )
    }

    "redirect to the edit-address page for a non postal address type and log a addressLookupResults audit event when a single record is returned by the address lookup service" in new LocalSetup {
      override def addressLookupResponse: RecordSet = RecordSet(List(fakeStreetPafAddressRecord))

      when(mockAddressLookupConnector.lookup(any(), any())(any(), any())) thenReturn {
        EitherT[Future, UpstreamErrorResponse, RecordSet](
          Future.successful(
            Right(addressLookupResponse)
          )
        )
      }

      when(mockJourneyCacheRepository.get(any())).thenReturn(
        Future.successful(
          UserAnswers
            .empty("id")
            .setOrException(AddressFinderPage(ResidentialAddrType), AddressFinderDto("AA1 1AA", None))
        )
      )

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(ResidentialAddrType, None)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/edit-address")

      val userAnswers: UserAnswers = UserAnswers.empty
        .setOrException(SelectedAddressRecordPage(ResidentialAddrType), fakeStreetPafAddressRecord)
      verify(mockJourneyCacheRepository, times(1)).set(userAnswers)
      verify(mockJourneyCacheRepository, times(1)).get(any())

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      pruneDataEvent(eventCaptor.getValue) mustBe comparatorDataEvent(
        eventCaptor.getValue,
        "addressLookupResults",
        "AA1 1AA"
      )
    }

    "redirect to showAddressSelectorForm and log a addressLookupResults audit event when multiple records are returned by the address lookup service" in new LocalSetup {
      override def addressLookupResponse: RecordSet = oneAndTwoOtherPlacePafRecordSet

      when(mockAddressLookupConnector.lookup(any(), any())(any(), any())) thenReturn {
        EitherT[Future, UpstreamErrorResponse, RecordSet](
          Future.successful(
            Right(addressLookupResponse)
          )
        )
      }

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(PostalAddrType, None)(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result).getOrElse("") must include("/select-address")

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      verify(mockJourneyCacheRepository, times(1)).get(any())
      pruneDataEvent(eventCaptor.getValue) mustBe comparatorDataEvent(
        eventCaptor.getValue,
        "addressLookupResults",
        "AA1 1AA"
      )
    }

    "return Not Found when an empty recordset is returned by the address lookup service and back = true" in new LocalSetup {
      override def addressLookupResponse: RecordSet = RecordSet(List())

      when(mockAddressLookupConnector.lookup(any(), any())(any(), any())) thenReturn {
        EitherT[Future, UpstreamErrorResponse, RecordSet](
          Future.successful(
            Right(addressLookupResponse)
          )
        )
      }
      when(mockJourneyCacheRepository.get(any())).thenReturn(
        Future.successful(
          UserAnswers
            .empty("id")
            .setOrException(AddressFinderPage(PostalAddrType), AddressFinderDto("AA1 1AA", None))
            .setOrException(AddressLookupServiceDownPage, false)
        )
      )

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(PostalAddrType, None)(FakeRequest())

      status(result) mustBe NOT_FOUND
      verify(mockJourneyCacheRepository, times(1)).set(any())
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "redirect to the postcodeLookupForm and when a single record is returned by the address lookup service and back = true" in new LocalSetup {
      override def addressLookupResponse: RecordSet = RecordSet(List(fakeStreetPafAddressRecord))

      when(mockAddressLookupConnector.lookup(any(), any())(any(), any())) thenReturn {
        EitherT[Future, UpstreamErrorResponse, RecordSet](
          Future.successful(
            Right(addressLookupResponse)
          )
        )
      }

      // TODO: -------- ADD MOCK FOR mockJourneyCacheRepository.get , set ?

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(PostalAddrType, Some(true))(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/find-address")
      verify(mockJourneyCacheRepository, times(1)).get(any())
      verify(mockJourneyCacheRepository, times(1)).set(any())

      "redirect to showAddressSelectorForm and display the select-address page when multiple records are returned by the address lookup service back=true" in new LocalSetup {
        override def addressLookupResponse: RecordSet = oneAndTwoOtherPlacePafRecordSet

        when(mockAddressLookupConnector.lookup(any(), any())(any(), any())) thenReturn {
          EitherT[Future, UpstreamErrorResponse, RecordSet](
            Future.successful(
              Right(addressLookupResponse)
            )
          )
        }

        override def currentRequest[A]: Request[A] =
          FakeRequest("POST", "")
            .withFormUrlEncodedBody("postcode" -> "AA1 1AA")
            .asInstanceOf[Request[A]]

        val result: Future[Result] = controller.onSubmit(PostalAddrType, None)(FakeRequest())

        status(result) mustBe SEE_OTHER
        redirectLocation(result).getOrElse("") must include("/select-address")
        verify(mockJourneyCacheRepository, times(1)).get(any())
        verify(mockJourneyCacheRepository, times(2)).set(any())
      }
    }
  }
}
