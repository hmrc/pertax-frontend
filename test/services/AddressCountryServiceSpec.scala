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

  "AddressCountryService.deriveCountryForPostcode" must {

    "return None when postcode is not provided" in {
      val result = await(service.deriveCountryForPostcode(None))
      result mustBe None
    }

    "return None when postcode is only whitespace" in {
      val result = await(service.deriveCountryForPostcode(Some("   ")))
      result mustBe None
    }

    "return a single normalised country when all addresses share the same country" in {
      val recordSet = RecordSet(
        Seq(
          addressRecord(Country.Scotland),
          addressRecord(Country.Scotland, Seq("2 TEST STREET"), "EH1 1AA")
        )
      )

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val result = await(service.deriveCountryForPostcode(Some("EH1 1AA")))

      val expected =
        Some(normalizationUtils.normalizeCountryName(Some(Country.Scotland.name)))

      result mustBe expected
    }

    "return None when multiple different countries are associated with the postcode" in {
      val scottish  = addressRecord(Country.Scotland)
      val english   = addressRecord(Country.England, Seq("2 TEST STREET"), "EH1 1AA")
      val recordSet = RecordSet(Seq(scottish, english))

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val result = await(service.deriveCountryForPostcode(Some("EH1 1AA")))

      result mustBe None
    }

    "return None when lookup returns an empty address list" in {
      val recordSet = RecordSet(Seq.empty)

      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](recordSet))

      val result = await(service.deriveCountryForPostcode(Some("EH1 1AA")))

      result mustBe None
    }

    "return None when connector lookup fails" in {
      when(mockConnector.lookup(meq("EH1 1AA"), meq(None))(any(), any()))
        .thenReturn(EitherT.leftT[Future, RecordSet](UpstreamErrorResponse("boom", 500)))

      val result = await(service.deriveCountryForPostcode(Some("EH1 1AA")))

      result mustBe None
    }
  }
}
