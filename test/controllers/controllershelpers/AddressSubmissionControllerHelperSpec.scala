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

package controllers.controllershelpers

import cats.data.EitherT
import config.ConfigDecorator
import controllers.bindable.{PostalAddrType, ResidentialAddrType}
import error.ErrorRenderer
import models.*
import models.dto.InternationalAddressChoiceDto.OutsideUK
import models.dto.{AddressDto, DateDto, InternationalAddressChoiceDto}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api
import play.api.i18n.{Lang, Messages, MessagesImpl, MessagesProvider}
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status}
import services.{AddressMovedService, CitizenDetailsService}
import testUtils.BaseSpec
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, OK}
import play.api.mvc.Request
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.language.LanguageUtils
import views.html.personaldetails.{CannotUpdateAddressEarlyDateView, UpdateAddressConfirmationView}
import testUtils.Fixtures.buildPersonDetails

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AddressSubmissionControllerHelperSpec extends BaseSpec {

  val mockAddressMovedService: AddressMovedService                               = mock[AddressMovedService]
  val mockAuditConnector: AuditConnector                                         = mock[AuditConnector]
  val injectedErrorRenderer: ErrorRenderer                                       = inject[ErrorRenderer]
  val injectedUpdateAddressConfirmationView: UpdateAddressConfirmationView       = inject[UpdateAddressConfirmationView]
  val mockCitizenDetailsService: CitizenDetailsService                           = mock[CitizenDetailsService]
  val injectedCannotUpdateAddressEarlyDateView: CannotUpdateAddressEarlyDateView =
    inject[CannotUpdateAddressEarlyDateView]
  val mockLanguageUtils: LanguageUtils                                           = mock[LanguageUtils]
  val mockConfigDecorator: ConfigDecorator                                       = mock[ConfigDecorator]

  implicit lazy val messageProvider: MessagesProvider = inject[MessagesProvider]
  implicit lazy val messages: Messages                = MessagesImpl(Lang("en"), messagesApi)

  val fakeRequest: Request[Any] =
    fakeScaRequest("GET", "")
      .asInstanceOf[Request[Any]]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockAddressMovedService,
      mockAuditConnector,
      mockCitizenDetailsService,
      mockLanguageUtils,
      mockEditAddressLockRepository,
      mockConfigDecorator
    )
  }

  val sut = new AddressSubmissionControllerHelper(
    mockAddressMovedService,
    mockEditAddressLockRepository,
    mockAuditConnector,
    injectedErrorRenderer,
    injectedUpdateAddressConfirmationView,
    mockCitizenDetailsService,
    injectedCannotUpdateAddressEarlyDateView,
    mockLanguageUtils
  )(mockConfigDecorator, global)

  "Calling updateCitizenDetailsAddress" must {
    "update address" when {
      "address is residential" in {
        when(mockCitizenDetailsService.updateAddress(any(), any(), any(), any())(any(), any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](true))
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(
          Future.successful(AuditResult.Success)
        )
        when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(
          Future.successful(true)
        )
        when(mockCitizenDetailsService.clearCachedPersonDetails(any())(any())).thenReturn(
          Future.unit
        )
        when(mockAddressMovedService.moved(any(), any(), any())(any(), any())).thenReturn(
          Future.successful(AnyOtherMove)
        )
        when(mockAddressMovedService.toMessageKey(any())).thenReturn(
          Some("label.mockAddressMovedService")
        )

        val addressJourneyData = AddressJourneyData(
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          false
        )

        val address: AddressDto =
          AddressDto("AddressLine1", Some("AddressLine2"), None, None, None, Some("TestPostcode"), None, None)

        val result = sut.updateCitizenDetailsAddress(
          generatedNino,
          ResidentialAddrType,
          addressJourneyData,
          buildPersonDetails,
          address
        )(hc, buildUserRequest(request = fakeRequest))

        status(result) mustBe OK
        contentAsString(result) must include("label.mockAddressMovedService")
        contentAsString(result) must include("You can only update your main address once a day")
        contentAsString(result) must not include "Leaving the UK"
        contentAsString(result) must not include "Your postal address has been changed to your main address"

        verify(mockAuditConnector, times(1)).sendEvent(any())(any(), any())
        verify(mockCitizenDetailsService, times(1)).updateAddress(any(), any(), any(), any())(any(), any(), any())
        verify(mockEditAddressLockRepository, times(1)).insert(any(), any())
        verify(mockCitizenDetailsService, times(1)).clearCachedPersonDetails(any())(any())
        verify(mockAddressMovedService, times(1)).moved(any(), any(), any())(any(), any())
        verify(mockAddressMovedService, times(1)).toMessageKey(any())
      }

      "address is postal" in {
        when(mockCitizenDetailsService.updateAddress(any(), any(), any(), any())(any(), any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](true))
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(
          Future.successful(AuditResult.Success)
        )
        when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(
          Future.successful(true)
        )
        when(mockCitizenDetailsService.clearCachedPersonDetails(any())(any())).thenReturn(
          Future.unit
        )
        when(mockAddressMovedService.moved(any(), any(), any())(any(), any())).thenReturn(
          Future.successful(AnyOtherMove)
        )
        when(mockAddressMovedService.toMessageKey(any())).thenReturn(
          Some("label.mockAddressMovedService")
        )

        val addressJourneyData = AddressJourneyData(
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          None,
          false
        )

        val address: AddressDto =
          AddressDto("AddressLine1", Some("AddressLine2"), None, None, None, Some("TestPostcode"), None, None)

        val result = sut.updateCitizenDetailsAddress(
          generatedNino,
          PostalAddrType,
          addressJourneyData,
          buildPersonDetails,
          address
        )(hc, buildUserRequest(request = fakeRequest))

        status(result) mustBe OK
        contentAsString(result) must not include "label.mockAddressMovedService"
        contentAsString(result) must not include "You can only update your main address once a day"
        contentAsString(result) must not include "Leaving the UK"
        contentAsString(result) must not include "Your postal address has been changed to your main address"

        verify(mockAuditConnector, times(1)).sendEvent(any())(any(), any())
        verify(mockCitizenDetailsService, times(1)).updateAddress(any(), any(), any(), any())(any(), any(), any())
        verify(mockEditAddressLockRepository, times(1)).insert(any(), any())
        verify(mockCitizenDetailsService, times(1)).clearCachedPersonDetails(any())(any())
        verify(mockAddressMovedService, times(1)).moved(any(), any(), any())(any(), any())
        verify(mockAddressMovedService, times(1)).toMessageKey(any())
      }

      "address is international" in {
        when(mockCitizenDetailsService.updateAddress(any(), any(), any(), any())(any(), any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](true))
        when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(
          Future.successful(AuditResult.Success)
        )
        when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(
          Future.successful(true)
        )
        when(mockCitizenDetailsService.clearCachedPersonDetails(any())(any())).thenReturn(
          Future.unit
        )
        when(mockAddressMovedService.moved(any(), any(), any())(any(), any())).thenReturn(
          Future.successful(AnyOtherMove)
        )
        when(mockAddressMovedService.toMessageKey(any())).thenReturn(
          Some("label.mockAddressMovedService")
        )

        val addressJourneyData = AddressJourneyData(
          None,
          None,
          None,
          None,
          None,
          None,
          Some(OutsideUK),
          None,
          false
        )

        val address: AddressDto =
          AddressDto("AddressLine1", Some("AddressLine2"), None, None, None, Some("TestPostcode"), None, None)

        val result = sut.updateCitizenDetailsAddress(
          generatedNino,
          ResidentialAddrType,
          addressJourneyData,
          buildPersonDetails,
          address
        )(hc, buildUserRequest(request = fakeRequest))

        status(result) mustBe OK
        contentAsString(result) must not include "label.mockAddressMovedService"
        contentAsString(result) must include("You can only update your main address once a day")
        contentAsString(result) must include("Leaving the UK")
        contentAsString(result) must not include "Your postal address has been changed to your main address"

        verify(mockAuditConnector, times(1)).sendEvent(any())(any(), any())
        verify(mockCitizenDetailsService, times(1)).updateAddress(any(), any(), any(), any())(any(), any(), any())
        verify(mockEditAddressLockRepository, times(1)).insert(any(), any())
        verify(mockCitizenDetailsService, times(1)).clearCachedPersonDetails(any())(any())
        verify(mockAddressMovedService, times(1)).moved(any(), any(), any())(any(), any())
        verify(mockAddressMovedService, times(1)).toMessageKey(any())
      }
    }

    "return start date error response" in {
      when(mockCitizenDetailsService.updateAddress(any(), any(), any(), any())(any(), any(), any()))
        .thenReturn(
          EitherT.leftT[Future, Boolean](
            UpstreamErrorResponse(
              "Start date is before current address start date",
              BAD_REQUEST,
              BAD_REQUEST
            )
          )
        )
      when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(
        Future.successful(AuditResult.Success)
      )
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(
        Future.successful(true)
      )
      when(mockCitizenDetailsService.clearCachedPersonDetails(any())(any())).thenReturn(
        Future.unit
      )
      when(mockAddressMovedService.moved(any(), any(), any())(any(), any())).thenReturn(
        Future.successful(AnyOtherMove)
      )
      when(mockAddressMovedService.toMessageKey(any())).thenReturn(
        Some("label.mockAddressMovedService")
      )

      val addressJourneyData = AddressJourneyData(
        None,
        None,
        None,
        None,
        None,
        None,
        Some(OutsideUK),
        Some(DateDto(LocalDate.of(2000, 1, 1))),
        false
      )

      val address: AddressDto =
        AddressDto("AddressLine1", Some("AddressLine2"), None, None, None, Some("TestPostcode"), None, None)

      val result = sut.updateCitizenDetailsAddress(
        generatedNino,
        ResidentialAddrType,
        addressJourneyData,
        buildPersonDetails,
        address
      )(hc, buildUserRequest(request = fakeRequest))

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("1 January 2000")
      contentAsString(result) must include("The date you entered is earlier than the one HMRC already has for you")
      contentAsString(result) must include("Leaving the UK")

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(any(), any(), any(), any())(any(), any(), any())
      verify(mockEditAddressLockRepository, times(0)).insert(any(), any())
      verify(mockCitizenDetailsService, times(0)).clearCachedPersonDetails(any())(any())
      verify(mockAddressMovedService, times(0)).moved(any(), any(), any())(any(), any())
      verify(mockAddressMovedService, times(0)).toMessageKey(any())
    }

    "return an error" in {
      when(mockCitizenDetailsService.updateAddress(any(), any(), any(), any())(any(), any(), any()))
        .thenReturn(
          EitherT.leftT[Future, Boolean](
            UpstreamErrorResponse(
              "Server error",
              INTERNAL_SERVER_ERROR,
              INTERNAL_SERVER_ERROR
            )
          )
        )
      when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(
        Future.successful(AuditResult.Success)
      )
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(
        Future.successful(true)
      )
      when(mockCitizenDetailsService.clearCachedPersonDetails(any())(any())).thenReturn(
        Future.unit
      )
      when(mockAddressMovedService.moved(any(), any(), any())(any(), any())).thenReturn(
        Future.successful(AnyOtherMove)
      )
      when(mockAddressMovedService.toMessageKey(any())).thenReturn(
        Some("label.mockAddressMovedService")
      )

      val addressJourneyData = AddressJourneyData(
        None,
        None,
        None,
        None,
        None,
        None,
        Some(OutsideUK),
        Some(DateDto(LocalDate.of(2000, 1, 1))),
        false
      )

      val address: AddressDto =
        AddressDto("AddressLine1", Some("AddressLine2"), None, None, None, Some("TestPostcode"), None, None)

      val result = sut.updateCitizenDetailsAddress(
        generatedNino,
        ResidentialAddrType,
        addressJourneyData,
        buildPersonDetails,
        address
      )(hc, buildUserRequest(request = fakeRequest))

      status(result) mustBe INTERNAL_SERVER_ERROR

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(any(), any(), any(), any())(any(), any(), any())
      verify(mockEditAddressLockRepository, times(0)).insert(any(), any())
      verify(mockCitizenDetailsService, times(0)).clearCachedPersonDetails(any())(any())
      verify(mockAddressMovedService, times(0)).moved(any(), any(), any())(any(), any())
      verify(mockAddressMovedService, times(0)).toMessageKey(any())
    }
  }

  "Calling handleAddressChangeAuditing" must {
    "audit when not modified" in {
      when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(
        Future.successful(AuditResult.Success)
      )

      val addressDto: AddressDto =
        AddressDto("AddressLine1", Some("AddressLine2"), None, None, None, Some("TestPostcode"), None, None)

      val result = sut
        .handleAddressChangeAuditing(
          Some(addressDto),
          addressDto,
          "etag123",
          "Residential"
        )(hc, buildUserRequest(request = fakeRequest))
        .futureValue

      result mustBe AuditResult.Success

      val arg                  = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent: DataEvent = arg.getValue
      dataEvent.auditType mustBe "postcodeAddressSubmitted"
    }

    "audit when heavily modified" in {
      when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(
        Future.successful(AuditResult.Success)
      )

      val addressDto: AddressDto =
        AddressDto("AddressLine1", Some("AddressLine2"), None, None, None, Some("TestPostcode"), None, None)

      val addressTwoDto: AddressDto =
        AddressDto("AddressLine2", Some("AddressLine3"), None, None, None, Some("OtherPostcode"), None, None)

      val result = sut
        .handleAddressChangeAuditing(
          Some(addressDto),
          addressTwoDto,
          "etag123",
          "Residential"
        )(hc, buildUserRequest(request = fakeRequest))
        .futureValue

      result mustBe AuditResult.Success

      val arg                  = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent: DataEvent = arg.getValue
      dataEvent.auditType mustBe "manualAddressSubmitted"
    }

    "audit when slightly modified" in {
      when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(
        Future.successful(AuditResult.Success)
      )

      val addressDto: AddressDto =
        AddressDto("AddressLine1", Some("AddressLine2"), None, None, None, Some("TestPostcode"), None, None)

      val addressTwoDto: AddressDto =
        AddressDto("AddressLine2", Some("AddressLine3"), None, None, None, Some("TestPostcode"), None, None)

      val result = sut
        .handleAddressChangeAuditing(
          Some(addressDto),
          addressTwoDto,
          "etag123",
          "Residential"
        )(hc, buildUserRequest(request = fakeRequest))
        .futureValue

      result mustBe AuditResult.Success

      val arg                  = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent: DataEvent = arg.getValue
      dataEvent.auditType mustBe "postcodeAddressModifiedSubmitted"
    }
  }

  "Calling isSubmittedAddressStartDateValid" must {
    "return true when start date is valid and address type is residential" in {
      val validDateDto = DateDto(LocalDate.now().minusDays(1))

      val resultForResidential = sut.isSubmittedAddressStartDateValid(
        Some(validDateDto),
        ResidentialAddrType
      )
      resultForResidential mustBe true

      val resultForPostal = sut.isSubmittedAddressStartDateValid(
        None,
        PostalAddrType
      )
      resultForPostal mustBe true
    }

    "return false when start date is missing for residential address" in {
      val result = sut.isSubmittedAddressStartDateValid(
        None,
        ResidentialAddrType
      )
      result mustBe false
    }

    "return true when address type is postal" in {
      val resultForResidential = sut.isSubmittedAddressStartDateValid(
        None,
        PostalAddrType
      )
      resultForResidential mustBe true

      val resultForPostal = sut.isSubmittedAddressStartDateValid(
        None,
        PostalAddrType
      )
      resultForPostal mustBe true
    }
  }
}
