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
import play.api.test.Helpers.await
import testUtils.BaseSpec
import uk.gov.hmrc.http.UpstreamErrorResponse
import play.api.test.Helpers.defaultAwaitTimeout

import scala.concurrent.Future

class AddressCountryServiceSpec extends BaseSpec {

  val mockConnector: AddressLookupConnector = mock[AddressLookupConnector]
  val normalizationUtils                    = new NormalizationUtils

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

  private def addressRecord(country: Country): AddressRecord =
    AddressRecord(
      id = "1",
      address = Address(
        lines = List("1 Test Street"),
        town = Some("TestTown"),
        county = Some("TestCounty"),
        postcode = "EH1 1AA",
        subdivision = Some(country),
        country = country
      ),
      language = "en"
    )

  "AddressCountryService.isCrossBorderScotland" must {

    "return true when old country is Scotland and new choice is England" in {
      val recordSet = RecordSet(Seq(addressRecord(Country.Scotland)))

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val resultF = service.isCrossBorderScotland(Some("EH1 1AA"), Some(InternationalAddressChoiceDto.England))
      await(resultF) mustBe true
    }

    "return false when old country is England and new choice is OutsideUK (cross-border to Scotland check)" in {
      val recordSet = RecordSet(Seq(addressRecord(Country.England)))

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val resultF = service.isCrossBorderScotland(Some("EH1 1AA"), Some(InternationalAddressChoiceDto.OutsideUK))
      await(resultF) mustBe false
    }

    "return false when both old and new are England" in {
      val recordSet = RecordSet(Seq(addressRecord(Country.England)))

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val resultF = service.isCrossBorderScotland(Some("EH1 1AA"), Some(InternationalAddressChoiceDto.England))
      await(resultF) mustBe false
    }

    "return false when both old and new are Scotland" in {
      val recordSet = RecordSet(Seq(addressRecord(Country.Scotland)))

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val resultF = service.isCrossBorderScotland(Some("EH1 1AA"), Some(InternationalAddressChoiceDto.Scotland))
      await(resultF) mustBe false
    }

    "fail with exception when lookup returns empty addresses" in {
      val recordSet = RecordSet(Seq.empty)

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val ex = intercept[RuntimeException] {
        await(service.isCrossBorderScotland(Some("EH1 1AA"), Some(InternationalAddressChoiceDto.England)))
      }
      ex.getMessage must include("Address lookup failed or returned no results")
    }

    "fail with exception when postcode is missing" in {
      val ex = intercept[RuntimeException] {
        await(service.isCrossBorderScotland(None, Some(InternationalAddressChoiceDto.England)))
      }
      ex.getMessage must include("Missing postcode for cross-border check")
    }
  }
}
