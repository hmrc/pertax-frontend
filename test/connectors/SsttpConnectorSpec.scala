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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import config.ConfigDecorator
import models.sa.SsttpResponse
import play.api.Application
import play.api.libs.json.Json
import play.api.test.{DefaultAwaitTimeout, Injecting}
import testUtils.WireMockHelper
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.ExecutionContext

class SsttpConnectorSpec extends ConnectorSpec with WireMockHelper with DefaultAwaitTimeout with Injecting {

  server.start()
  override implicit lazy val app: Application = app(
    Map(
      "microservice.services.essttp-backend.port"           -> server.port(),
      "external-url.self-service-time-to-pay-pta-start.url" -> "/essttp-backend/sa/pta/journey/start"
    )
  )

  private implicit val hc: HeaderCarrier    = HeaderCarrier()
  private implicit val ec: ExecutionContext = inject[ExecutionContext]

  lazy val connector: SsttpConnector = {
    val httpClient      = app.injector.instanceOf[HttpClientV2]
    val configDecorator = app.injector.instanceOf[ConfigDecorator]
    new SsttpConnector(httpClient, configDecorator)
  }

  private val url = "/essttp-backend/sa/pta/journey/start"

  private val requestBodyJson = Json.obj("returnUrl" -> "/personal-account", "backUrl" -> "/personal-account")

  "SsttpConnector.startPtaJourney" should {

    "return Some(SsttpResponse) when essttp-backend returns 201 with valid JSON" in {
      val respJson = Json.obj("journeyId" -> "journey-123", "nextUrl" -> "/setup-a-payment-plan/pta")

      stubPost(url, CREATED, Some(requestBodyJson.toString()), Some(respJson.toString()))

      val result = connector.startPtaJourney().futureValue

      result mustBe Some(SsttpResponse("journey-123", "/setup-a-payment-plan/pta"))
      server.verify(postRequestedFor(urlEqualTo(url)).withRequestBody(equalToJson(requestBodyJson.toString())))
    }

    "return None when backend returns 201 but JSON is missing fields" in {
      val badJson = Json.obj("unexpected" -> "value")

      stubPost(url, CREATED, Some(requestBodyJson.toString()), Some(badJson.toString()))

      val result = connector.startPtaJourney().futureValue

      result mustBe None
      server.verify(postRequestedFor(urlEqualTo(url)).withRequestBody(equalToJson(requestBodyJson.toString())))
    }

    "return None when backend returns INTERNAL_SERVER_ERROR" in {
      val respJson = Json.obj("journeyId" -> "journey-123", "nextUrl" -> "/setup-a-payment-plan/pta")

      stubPost(url, INTERNAL_SERVER_ERROR, Some(requestBodyJson.toString()), Some(respJson.toString()))

      val result = connector.startPtaJourney().futureValue

      result mustBe None
      server.verify(postRequestedFor(urlEqualTo(url)).withRequestBody(equalToJson(requestBodyJson.toString())))
    }
  }
}
