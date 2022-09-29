/*
 * Copyright 2022 HM Revenue & Customs
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

import models.{CreatePayment, PaymentRequest}
import play.api.Application
import play.api.libs.json.{JsResultException, Json}
import play.api.test.DefaultAwaitTimeout
import play.api.test.Helpers.await
import testUtils.WireMockHelper

class PayApiConnectorSpec extends ConnectorSpec with WireMockHelper with DefaultAwaitTimeout {

  override lazy val app: Application = app(
    Map("microservice.services.pay-api.port" -> server.port())
  )

  def connector: PayApiConnector = app.injector.instanceOf[PayApiConnector]

  val paymentRequest: PaymentRequest = PaymentRequest("some utr", "", "")
  val url: String = "/pay-api/pta/sa/journey/start"

  "createPayment" should {
    val json = Json.obj(
      "journeyId" -> "exampleJourneyId",
      "nextUrl"   -> "testNextUrl"
    )

    "parse the json load for a successful CREATED response" in {
      stubPost(url, CREATED, Some(Json.toJson(paymentRequest).toString()), Some(json.toString()))
      val result = connector.createPayment(paymentRequest).futureValue

      result mustBe Some(CreatePayment("exampleJourneyId", "testNextUrl"))
    }

    "Returns a None when the status code is not CREATED" in {
      stubPost(url, NO_CONTENT, Some(Json.toJson(paymentRequest).toString()), Some(json.toString()))
      val result = connector.createPayment(paymentRequest).futureValue

      result mustBe None
    }

    "Throws a JsResultException when given bad json" in {
      val badJson = Json.obj("abc" -> "invalidData")

      stubPost(url, CREATED, Some(Json.toJson(paymentRequest).toString()), Some(badJson.toString()))
      lazy val result = await(connector.createPayment(paymentRequest))
      a[JsResultException] mustBe thrownBy(result)
    }
  }
}
