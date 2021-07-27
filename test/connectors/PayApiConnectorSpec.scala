/*
 * Copyright 2021 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import play.api.http.Status._
import play.api.libs.json.{JsResultException, Json}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import util.{BaseSpec, NullMetrics}

import scala.concurrent.Future

class PayApiConnectorSpec extends BaseSpec {

  val http = mock[DefaultHttpClient]
  val connector = new PayApiConnector(http, config, new NullMetrics)
  val paymentRequest = PaymentRequest(config, "some utr")
  val postUrl = config.makeAPaymentUrl

  "createPayment" should {
    "parse the json load for a successful CREATED response" in {
      val json = Json.obj(
        "journeyId" -> "exampleJourneyId",
        "nextUrl"   -> "testNextUrl"
      )

      when(
        http.POST[PaymentRequest, HttpResponse](eqTo(postUrl), eqTo(paymentRequest), any())(any(), any(), any(), any())
      )
        .thenReturn(Future.successful(HttpResponse(CREATED, Some(json))))

      connector.createPayment(paymentRequest).futureValue mustBe Some(CreatePayment("exampleJourneyId", "testNextUrl"))
    }

    "Returns a None when the status code is not CREATED" in {
      when(
        http.POST[PaymentRequest, HttpResponse](eqTo(postUrl), eqTo(paymentRequest), any())(any(), any(), any(), any())
      )
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST)))

      connector.createPayment(paymentRequest).futureValue mustBe None
    }

    "Throws a JsResultException when given bad json" in {
      val badJson = Json.obj("abc" -> "invalidData")

      when(
        http.POST[PaymentRequest, HttpResponse](eqTo(postUrl), eqTo(paymentRequest), any())(any(), any(), any(), any())
      )
        .thenReturn(Future.successful(HttpResponse(CREATED, Some(badJson))))

      val f = connector.createPayment(paymentRequest)
      whenReady(f.failed) { e =>
        e mustBe a[JsResultException]
      }
    }
  }
}
