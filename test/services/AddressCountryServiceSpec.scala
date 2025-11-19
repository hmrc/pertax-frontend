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

package services

import cats.data.EitherT
import connectors.AddressLookupConnector
import models.addresslookup.{Address, AddressRecord, Country, RecordSet}
import models.dto.InternationalAddressChoiceDto
import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.{reset, when}
import play.api.Application
import play.api.inject.bind
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import testUtils.BaseSpec
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.Future

class AddressCountryServiceSpec extends BaseSpec {

  private val mockConnector: AddressLookupConnector = mock[AddressLookupConnector]
  private val normalizationUtils                    = new NormalizationUtils

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AddressLookupConnector].toInstance(mockConnector),
      bind[NormalizationUtils].toInstance(normalizationUtils)
    )
    .build()

  private lazy val service: AddressCountryService = app.injector.instanceOf[AddressCountryService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConnector)
  }

  private def addressRecord(
    country: Country,
    lines: Seq[String] = Seq("1 TEST STREET"),
    postcode: String = "EH1 1AA"
  ): AddressRecord =
    AddressRecord(
      id = "1",
      address = Address(
        lines = lines.toList,
        town = Some("TESTTOWN"),
        county = Some("TESTCOUNTY"),
        postcode = postcode,
        subdivision = Some(country),
        country = country
      ),
      language = "en"
    )

  "AddressCountryService.isCrossBorderScotland" must {

    "return true when old country is Scotland and new choice is England (cross-border)" in {
      val recordSet = RecordSet(Seq(addressRecord(Country.Scotland)))

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val resultF =
        service.isCrossBorderScotland(
          currentAddressLines = Seq("1 TEST STREET"),
          currentPostcode = Some("EH1 1AA"),
          newInternationalChoice = Some(InternationalAddressChoiceDto.England)
        )

      await(resultF) mustBe true
    }

    "return false when old country is England and new choice is OutsideUK (not a Scotland cross-border move)" in {
      val recordSet = RecordSet(Seq(addressRecord(Country.England)))

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val resultF =
        service.isCrossBorderScotland(
          currentAddressLines = Seq("1 TEST STREET"),
          currentPostcode = Some("EH1 1AA"),
          newInternationalChoice = Some(InternationalAddressChoiceDto.OutsideUK)
        )

      await(resultF) mustBe false
    }

    "return false when both old and new are England" in {
      val recordSet = RecordSet(Seq(addressRecord(Country.England)))

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val resultF =
        service.isCrossBorderScotland(
          currentAddressLines = Seq("1 TEST STREET"),
          currentPostcode = Some("EH1 1AA"),
          newInternationalChoice = Some(InternationalAddressChoiceDto.England)
        )

      await(resultF) mustBe false
    }

    "return false when both old and new are Scotland" in {
      val recordSet = RecordSet(Seq(addressRecord(Country.Scotland)))

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val resultF =
        service.isCrossBorderScotland(
          currentAddressLines = Seq("1 TEST STREET"),
          currentPostcode = Some("EH1 1AA"),
          newInternationalChoice = Some(InternationalAddressChoiceDto.Scotland)
        )

      await(resultF) mustBe false
    }

    "return true when lookup returns empty addresses (safe default)" in {
      val recordSet = RecordSet(Seq.empty)

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val resultF =
        service.isCrossBorderScotland(
          currentAddressLines = Seq("1 TEST STREET"),
          currentPostcode = Some("EH1 1AA"),
          newInternationalChoice = Some(InternationalAddressChoiceDto.England)
        )

      await(resultF) mustBe true
    }

    "return true when connector lookup fails (safe default)" in {
      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.leftT[Future, RecordSet](UpstreamErrorResponse("boom", 500)))

      val resultF =
        service.isCrossBorderScotland(
          currentAddressLines = Seq("1 TEST STREET"),
          currentPostcode = Some("EH1 1AA"),
          newInternationalChoice = Some(InternationalAddressChoiceDto.England)
        )

      await(resultF) mustBe true
    }

    "return true when postcode is missing" in {
      val resultF =
        service.isCrossBorderScotland(
          currentAddressLines = Seq("1 TEST STREET"),
          currentPostcode = None,
          newInternationalChoice = Some(InternationalAddressChoiceDto.England)
        )

      await(resultF) mustBe true
    }

    "return false for multiple addresses when one matches by lines + postcode and move is England to England" in {
      val record1   = addressRecord(Country.England, Seq("1 TEST STREET"), "EH1 1AA")
      val record2   = addressRecord(Country.Scotland, Seq("2 OTHER STREET"), "EH1 1AA")
      val recordSet = RecordSet(Seq(record1, record2))

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val resultF =
        service.isCrossBorderScotland(
          currentAddressLines = Seq("1 TEST STREET"),
          currentPostcode = Some("EH1 1AA"),
          newInternationalChoice = Some(InternationalAddressChoiceDto.England)
        )

      await(resultF) mustBe false
    }

    "return true for multiple addresses when none match by lines + postcode (safe default cross-border=true)" in {
      val record1   = addressRecord(Country.England, Seq("10 OTHER STREET"), "EH1 1AA")
      val record2   = addressRecord(Country.Scotland, Seq("11 OTHER STREET"), "EH1 1AA")
      val recordSet = RecordSet(Seq(record1, record2))

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val resultF =
        service.isCrossBorderScotland(
          currentAddressLines = Seq("1 TEST STREET"),
          currentPostcode = Some("EH1 1AA"),
          newInternationalChoice = Some(InternationalAddressChoiceDto.England)
        )

      await(resultF) mustBe true
    }

    "return false when new country choice is missing or not meaningful" in {
      val recordSet = RecordSet(Seq(addressRecord(Country.England)))

      when(mockConnector.lookup(any[String], any[Option[String]])(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val resultF =
        service.isCrossBorderScotland(
          currentAddressLines = Seq("1 TEST STREET"),
          currentPostcode = Some("EH1 1AA"),
          newInternationalChoice = None
        )

      await(resultF) mustBe false
    }
  }
}
