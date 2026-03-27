/*
 * Copyright 2026 HM Revenue & Customs
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

package controllers.tempAddressFix

import cats.data.EitherT
import models.tempAddressFix.{AddressFixRecord, FixStatus}
import models.{Address, PersonDetails}
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{reset, when}
import repositories.TempAddressFixRepository
import services.CitizenDetailsService
import connectors.CitizenDetailsConnector
import controllers.bindable.ResidentialAddrType
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.BaseSpec
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.UpstreamErrorResponse
import testUtils.Fixtures
import models.tempAddressFix.ErrorResult
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND}

import java.time.{Instant, LocalDate}
import scala.concurrent.Future

class FixControllerHelperSpec extends BaseSpec {

  val mockTempAddressFixRepository: TempAddressFixRepository = mock[TempAddressFixRepository]
  val mockCitizenDetailsService: CitizenDetailsService       = mock[CitizenDetailsService]
  val mockCitizenDetailsConnector: CitizenDetailsConnector   = mock[CitizenDetailsConnector]

  val helper = new FixControllerHelper(
    mockTempAddressFixRepository,
    mockCitizenDetailsService,
    mockCitizenDetailsConnector,
    mockEditAddressLockRepository
  )

  val nino: String = generatedNino.nino
  val record       = AddressFixRecord(nino, "postcode1", FixStatus.Todo, Instant.now())

  def fakeAddress(
    line1: Option[String] = None,
    line2: Option[String] = None,
    line3: Option[String] = None,
    line4: Option[String] = None,
    line5: Option[String] = None,
    postcode: Option[String] = None,
    country: Option[String] = None,
    startDate: Option[LocalDate] = None,
    endDate: Option[LocalDate] = None,
    `type`: Option[String] = None,
    isRls: Boolean = false
  ) = Address(line1, line2, line3, line4, line5, postcode, country, startDate, endDate, `type`, isRls)

  val abroadAddress: Address = fakeAddress(
    country = Some("ABROAD - NOT KNOWN"),
    postcode = None
  )

  val normalAddress: Address = fakeAddress(
    country = None,
    postcode = Some("postcode")
  )

  def fakePersonDetails(
    address: Option[Address] = None,
    correspondenceAddress: Option[Address] = None,
    etag: String = "etag123"
  ) =
    Fixtures.buildFakePersonDetails.copy(address = address, correspondenceAddress = correspondenceAddress, etag = etag)

  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = fakeScaRequest()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTempAddressFixRepository, mockCitizenDetailsService, mockCitizenDetailsConnector)
  }

  "fixAddress" when {

    "residential address is ABROAD - NOT KNOWN" should {
      "call updateAddress and return Right(DoneResidential)" in {
        val details = fakePersonDetails(address = Some(abroadAddress))

        when(mockCitizenDetailsConnector.updateAddress(eqTo(Nino(nino)), eqTo("etag123"), any())(any(), any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](true))

        val result = helper.fixAddress(record, details).value.futureValue

        result mustBe Right(FixStatus.DoneResidential)
      }

      "pass the updated address with country removed and new postcode" in {
        val details = fakePersonDetails(address = Some(abroadAddress))

        when(
          mockCitizenDetailsConnector.updateAddress(
            eqTo(Nino(nino)),
            eqTo("etag123"),
            eqTo(abroadAddress.copy(country = None, postcode = Some(record.postcode)))
          )(any(), any(), any())
        )
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](true))

        val result = helper.fixAddress(record, details).value.futureValue

        result mustBe Right(FixStatus.DoneResidential)
      }
    }

    "correspondence address is ABROAD - NOT KNOWN" should {
      "call updateAddress and return Right(DoneCorrespondence)" in {
        val details = fakePersonDetails(
          address = Some(normalAddress),
          correspondenceAddress = Some(abroadAddress)
        )

        when(mockCitizenDetailsConnector.updateAddress(eqTo(Nino(nino)), eqTo("etag123"), any())(any(), any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](true))

        val result = helper.fixAddress(record, details).value.futureValue

        result mustBe Right(FixStatus.DoneCorrespondence)
      }
    }

    "neither address is ABROAD - NOT KNOWN" should {
      "return Right(SkippedNotAbroad) without calling updateAddress" in {
        val details = fakePersonDetails(address = Some(normalAddress))

        val result = helper.fixAddress(record, details).value.futureValue

        result mustBe Right(FixStatus.SkippedNotAbroad)
      }
    }
  }

  "processRecord" when {

    "the full happy path succeeds" should {
      "return Right(DoneResidential)" in {
        val details = fakePersonDetails(address = Some(abroadAddress))

        when(
          mockTempAddressFixRepository
            .findOneAndUpdate(eqTo(nino), eqTo(FixStatus.Processing), eqTo(Some(FixStatus.Todo)))
        )
          .thenReturn(Future.successful(Some(record)))

        when(mockCitizenDetailsService.personDetails(eqTo(Nino(nino)), any())(any(), any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(details)))

        when(mockCitizenDetailsConnector.updateAddress(eqTo(Nino(nino)), eqTo("etag123"), any())(any(), any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](true))

        when(mockTempAddressFixRepository.findOneAndUpdate(eqTo(nino), eqTo(FixStatus.DoneResidential), eqTo(None)))
          .thenReturn(Future.successful(Some(record.copy(status = FixStatus.DoneResidential))))

        when(mockEditAddressLockRepository.insert(eqTo(nino.take(8)), eqTo(ResidentialAddrType)))
          .thenReturn(Future.successful(true))

        val result = helper.processRecord(nino).value.futureValue

        result mustBe Right(FixStatus.DoneResidential)
      }
    }

    "no record found in mongo" should {
      "return error not found and not proceed further" in {
        when(
          mockTempAddressFixRepository
            .findOneAndUpdate(eqTo(nino), eqTo(FixStatus.Processing), eqTo(Some(FixStatus.Todo)))
        )
          .thenReturn(Future.successful(None))

        val result = helper.processRecord(nino).value.futureValue

        result mustBe Left(ErrorResult(NOT_FOUND, "No record found to fix"))
      }
    }

    "citizen details returns None" should {
      "return error not found" in {
        when(
          mockTempAddressFixRepository
            .findOneAndUpdate(eqTo(nino), eqTo(FixStatus.Processing), eqTo(Some(FixStatus.Todo)))
        )
          .thenReturn(Future.successful(Some(record)))

        when(mockCitizenDetailsService.personDetails(eqTo(Nino(nino)), any())(any(), any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](None))

        val result = helper.processRecord(nino).value.futureValue

        result mustBe Left(ErrorResult(NOT_FOUND, s"details not found for nino $nino"))
      }
    }

    "updateAddress fails" should {
      "return error InternalServerError" in {
        val details = fakePersonDetails(address = Some(abroadAddress))
        val error   = UpstreamErrorResponse("update failed", 500)

        when(
          mockTempAddressFixRepository
            .findOneAndUpdate(eqTo(nino), eqTo(FixStatus.Processing), eqTo(Some(FixStatus.Todo)))
        )
          .thenReturn(Future.successful(Some(record)))

        when(mockCitizenDetailsService.personDetails(eqTo(Nino(nino)), any())(any(), any(), any()))
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](Some(details)))

        when(mockCitizenDetailsConnector.updateAddress(eqTo(Nino(nino)), eqTo("etag123"), any())(any(), any(), any()))
          .thenReturn(EitherT.leftT[Future, Boolean](error))

        val result = helper.processRecord(nino).value.futureValue

        result mustBe Left(ErrorResult(INTERNAL_SERVER_ERROR, error.message))
      }
    }
  }
}
