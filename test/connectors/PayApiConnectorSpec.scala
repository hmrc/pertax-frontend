/*
 * Copyright 2019 HM Revenue & Customs
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

import models.PaymentRequest
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.Json
import services.http.WsAllMethods
import uk.gov.hmrc.http.{HttpException, HttpResponse}
import util.BaseSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class PayApiConnectorSpec extends BaseSpec with MockitoSugar {

  val http = mock[WsAllMethods]
  val connector = new PayApiConnector(http, config)
  val paymentRequest = PaymentRequest(config, "some utr")
  val postUrl = config.makeAPaymentUrl

  "createPayment" should {
    "parse the json load for a successful CREATED response" in {
      val json = Json.obj(
        "journeyId" -> "exampleJourneyId",
        "nextUrl"   -> "testNextUrl"
      )

      when(
        http.POST[PaymentRequest, HttpResponse](Matchers.eq(postUrl), Matchers.eq(paymentRequest), any())(
          any(),
          any(),
          any(),
          any()))
        .thenReturn(Future.successful(HttpResponse(CREATED, Some(json))))

      val result = Await.result(connector.createPayment(paymentRequest), 5.seconds)
      result shouldBe CreatePaymentResponse("exampleJourneyId", "testNextUrl")
    }

    "Returns a Http Exception when the statys code is not CREATED" in {
      when(
        http.POST[PaymentRequest, HttpResponse](Matchers.eq(postUrl), Matchers.eq(paymentRequest), any())(
          any(),
          any(),
          any(),
          any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST)))

      val result = the[HttpException] thrownBy Await.result(connector.createPayment(paymentRequest), 5.seconds)
      result.responseCode shouldBe BAD_REQUEST
    }
  }
}
