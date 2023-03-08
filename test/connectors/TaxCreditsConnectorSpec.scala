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

package connectors

import play.api.Application
import testUtils.Fixtures.fakeNino
import testUtils.{FileHelper, WireMockHelper}
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}

class TaxCreditsConnectorSpec extends ConnectorSpec with WireMockHelper {

  override lazy val app: Application = app(
    Map("microservice.services.tcs-broker.port" -> server.port())
  )

  def connector: TaxCreditsConnector = app.injector.instanceOf[TaxCreditsConnector]

  lazy val url: String = s"/tcs/$fakeNino/dashboard-data"

  "TaxCreditsConnector" when {
    "checkForTaxCredits is called" must {
      "return a HttpResponse containing OK if tcs data for the given NINO is found" in {
        val data     = FileHelper.loadFile("./test/resources/tcs/dashboard-data.json")
        stubGet(url, OK, Some(data))
        val response = connector.checkForTaxCredits(fakeNino).value.futureValue

        response mustBe a[Right[_, _]]

        val result = response.getOrElse(HttpResponse(IM_A_TEAPOT, "Invalid Response"))
        result.status mustBe OK
        result.body mustBe data
      }

      "return a UpstreamErrorException containing NOT_FOUND if tcs data for the given isn't found" in {
        stubGet(url, NOT_FOUND, None)
        val result = connector.checkForTaxCredits(fakeNino).value.futureValue

        result mustBe a[Left[_, _]]
        result.left mustBe UpstreamErrorResponse(_: String, NOT_FOUND)
      }

      List(
        BAD_REQUEST,
        IM_A_TEAPOT,
        INTERNAL_SERVER_ERROR,
        SERVICE_UNAVAILABLE
      ).foreach { status =>
        s"return an UpstreamErrorException containing INTERNAL_SERVER_ERROR when $status is returned from TCS Broker" in {
          stubGet(url, status, None)
          val result = connector.checkForTaxCredits(fakeNino).value.futureValue

          result mustBe a[Left[_, _]]
          result.left mustBe UpstreamErrorResponse(_: String, status)
        }
      }
    }
  }
}
