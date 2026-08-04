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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock.{getRequestedFor, matching, urlEqualTo}
import models.LeppSummaryResponse
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.WireMockHelper
import uk.gov.hmrc.http.UpstreamErrorResponse

class DefaultLeppConnectorSpec extends ConnectorSpec with WireMockHelper with IntegrationPatience {

  override implicit lazy val app: Application =
    app(
      Map(
        "microservice.services.low-earners-pensions-payment.port" -> server.port(),
        "microservice.services.low-earners-pensions-payment.timeoutInMilliseconds" -> 1000,
        "feature.low-earners-pensions-payment.maxTps" -> 1000
      )
    )

  private def connector: DefaultLeppConnector =
    app.injector.instanceOf[DefaultLeppConnector]

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  private val url = "/low-earners-pensions-payment/get-lepp-summary"

  "DefaultLeppConnector.getLeppSummary" must {

    "return the LEPP summary response and send a correlationId header" in {
      stubGet(url, OK, Some("""{"status":"PAYMENTS_AVAILABLE","data":{}}"""))

      val result = connector.getLeppSummary.value.futureValue

      result mustBe Right(LeppSummaryResponse("PAYMENTS_AVAILABLE"))
      server.verify(
        getRequestedFor(urlEqualTo(url)).withHeader(
          "correlationId",
          matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
        )
      )
    }

    "return Left when the LEPP backend returns an error" in {
      stubGet(url, INTERNAL_SERVER_ERROR, None)

      val result = connector.getLeppSummary.value.futureValue

      result mustBe a[Left[UpstreamErrorResponse, _]]
    }
  }
}
