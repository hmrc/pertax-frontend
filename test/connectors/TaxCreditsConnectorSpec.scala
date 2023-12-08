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
import testUtils.WireMockHelper
import uk.gov.hmrc.http.UpstreamErrorResponse

class TaxCreditsConnectorSpec extends ConnectorSpec with WireMockHelper {

  override lazy val app: Application = app(
    Map("microservice.services.tcs-broker.port" -> server.port())
  )

  def connector: TaxCreditsConnector = app.injector.instanceOf[TaxCreditsConnector]

  lazy val url: String = s"/tcs/$fakeNino/exclusion"

  "TaxCreditsConnector" when {
    "checkForTaxCredits is called" must {
      "return a boolean true value when excluded node in json response is true" in {
        val data = """{"excluded": true}"""
        stubGet(url, OK, Some(data))
        val response = connector.getTaxCreditsExclusionStatus(fakeNino).value.futureValue

        response mustBe a[Right[_, _]]

        val result = response.getOrElse(false)
        result mustBe true
      }

      "return a boolean false value when excluded node in json response is false" in {
        val data = """{"excluded": false}"""
        stubGet(url, OK, Some(data))
        val response = connector.getTaxCreditsExclusionStatus(fakeNino).value.futureValue

        response mustBe a[Right[_, _]]

        val result = response.getOrElse(false)
        result mustBe false
      }

      "return a UpstreamErrorException containing NOT_FOUND if tcs data for the given isn't found" in {
        stubGet(url, NOT_FOUND, None)
        val result = connector.getTaxCreditsExclusionStatus(fakeNino).value.futureValue

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
          val result = connector.getTaxCreditsExclusionStatus(fakeNino).value.futureValue

          result mustBe a[Left[_, _]]
          result.left mustBe UpstreamErrorResponse(_: String, status)
        }
      }
    }
  }
}

class TaxCreditsConnectorTimeoutSpec extends ConnectorSpec with WireMockHelper {

  override lazy val app: Application = app(
    Map(
      "microservice.services.tcs-broker.port" -> server.port(),
      "microservice.services.tcs-broker.timeoutInMilliseconds" -> 1
    )
  )

  def connector: TaxCreditsConnector = app.injector.instanceOf[TaxCreditsConnector]

  lazy val url: String = s"/tcs/$fakeNino/exclusion"

  "TaxCreditsConnector" when {
    "checkForTaxCredits is called" must {
      "return bad gateway when the call results in a timeout" in {
        def connector: TaxCreditsConnector = app.injector.instanceOf[TaxCreditsConnector]

        stubWithDelay(url, OK, None, None, 500)
        val result = connector.getTaxCreditsExclusionStatus(fakeNino).value.futureValue

        result mustBe a[Left[_, _]]
        result.left.getOrElse(UpstreamErrorResponse("", OK)).statusCode mustBe BAD_GATEWAY
      }
    }
  }
}
