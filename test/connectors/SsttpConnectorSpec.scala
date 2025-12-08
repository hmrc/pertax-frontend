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

import config.ConfigDecorator
import models.sa.SsttpResponse
import play.api.Application
import play.api.libs.json.{JsResultException, Json}
import play.api.test.{DefaultAwaitTimeout, Injecting}
import play.api.test.Helpers.await
import testUtils.WireMockHelper
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

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
    val httpClient                             = inject[HttpClientV2]
    val configDecorator                        = inject[ConfigDecorator]
    val httpClientResponse: HttpClientResponse = inject[HttpClientResponse]

    new SsttpConnector(httpClient, httpClientResponse, configDecorator)
  }

  private val url = "/essttp-backend/sa/pta/journey/start"

  private val requestBodyJson = Json.obj("returnUrl" -> "/personal-account", "backUrl" -> "/personal-account")

  "SsttpConnector.startPtaJourney" should {

    "return Right(SsttpResponse) when essttp-backend returns 201 with valid JSON" in {
      val respJson = Json.obj("journeyId" -> "journey-123", "nextUrl" -> "/setup-a-payment-plan/pta")

      stubPost(url, CREATED, Some(requestBodyJson.toString()), Some(respJson.toString()))

      val result = connector.startPtaJourney().value.futureValue

      result mustBe a[Right[_, _]]
      val expectedResponse = SsttpResponse("journey-123", "/setup-a-payment-plan/pta")
      result.getOrElse(None) mustBe expectedResponse
    }

    "throw a JsResultException when the response body is invalid" in {
      val badJson = Json.obj("invalid" -> "invalid")
      stubPost(url, OK, Some(requestBodyJson.toString()), Some(badJson.toString()))

      lazy val result = await(connector.startPtaJourney().value)
      a[JsResultException] mustBe thrownBy(result)
    }

    "return an UpstreamErrorResponse" when
      List(BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND).foreach(statusCode =>
        s"a status $statusCode is returned by the essttp-backend" in {
          stubPost(url, statusCode, Some(requestBodyJson.toString()), None)

          val result = connector.startPtaJourney().value.futureValue

          result mustBe a[Left[_, _]]
          result.swap.getOrElse(SsttpResponse("", "")) mustBe UpstreamErrorResponse(_: String, statusCode)
        }
      )
  }
}
