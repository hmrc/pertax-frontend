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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post}
import models.{PayApiModels, PaymentRequest}
import play.api.Application
import play.api.libs.json.Json
import play.api.test.DefaultAwaitTimeout
import testUtils.WireMockHelper
import uk.gov.hmrc.http.UpstreamErrorResponse

class PayApiConnectorSpec extends ConnectorSpec with WireMockHelper with DefaultAwaitTimeout {

  override lazy val app: Application = app(
    Map("microservice.services.pay-api.port" -> server.port())
  )

  def connector: PayApiConnector = app.injector.instanceOf[PayApiConnector]

  val paymentRequest: PaymentRequest = PaymentRequest("some utr", "", "")
  val url: String                    = "/pay-api/pta/sa/journey/start"

  "createPayment" should {
    val json = Json.obj(
      "journeyId" -> "exampleJourneyId",
      "nextUrl"   -> "testNextUrl"
    )

    "parse the json load for a successful CREATED response" in {
      stubPost(url, CREATED, Some(Json.toJson(paymentRequest).toString()), Some(json.toString()))
      val result = connector.createPayment(paymentRequest).value.futureValue.getOrElse(None)

      result mustBe Some(PayApiModels("exampleJourneyId", "testNextUrl"))
    }

    "returns a None when the status code is not CREATED" in {
      server.stubFor(
        post(url).willReturn(
          aResponse().withStatus(CREATED).withBody(Json.obj("unrelatedField" -> "").toString)
        )
      )
      val result = connector
        .createPayment(paymentRequest)
        .value
        .futureValue
        .getOrElse(Some(PayApiModels("exampleJourneyId", "testNextUrl")))

      result mustBe None
    }

    List(
      BAD_REQUEST,
      NOT_FOUND,
      REQUEST_TIMEOUT,
      UNPROCESSABLE_ENTITY,
      INTERNAL_SERVER_ERROR,
      BAD_GATEWAY,
      SERVICE_UNAVAILABLE
    ).foreach { error =>
      s"Returns an UpstreamErrorResponse when the status code is $error" in {
        stubPost(url, error, Some(Json.toJson(paymentRequest).toString()), Some(json.toString()))
        val result =
          connector.createPayment(paymentRequest).value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK))

        result.statusCode mustBe error
      }
    }
  }
}
