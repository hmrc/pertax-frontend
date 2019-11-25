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

import java.time.{LocalDate, LocalDateTime, LocalTime}

import models.PaymentRequest
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.{JsResultException, Json}
import services.http.WsAllMethods
import uk.gov.hmrc.http.HttpResponse
import util.BaseSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PayApiConnectorSpec extends BaseSpec with MockitoSugar with ScalaFutures {

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
        http.POST[PaymentRequest, HttpResponse](eqTo(postUrl), eqTo(paymentRequest), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(CREATED, Some(json))))

      connector.createPayment(paymentRequest).futureValue shouldBe Some(
        CreatePayment("exampleJourneyId", "testNextUrl"))
    }

    "Returns a None when the status code is not CREATED" in {
      when(
        http.POST[PaymentRequest, HttpResponse](eqTo(postUrl), eqTo(paymentRequest), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST)))

      connector.createPayment(paymentRequest).futureValue shouldBe None
    }

    "Throws a JsResultException when given bad json" in {
      val badJson = Json.obj("abc" -> "invalidData")

      when(
        http.POST[PaymentRequest, HttpResponse](eqTo(postUrl), eqTo(paymentRequest), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(CREATED, Some(badJson))))

      val f = connector.createPayment(paymentRequest)
      whenReady(f.failed) { e =>
        e shouldBe a[JsResultException]
      }
    }
  }

  "findPayments" should {

    "parse the json load for a successful OK response" in {

      val json = Json.parse("""
                              |{
                              |   "searchScope":"PTA",
                              |   "searchTag":"1097172564",
                              |   "payments":[
                              |      {
                              |         "id":"5ddbd2847a0000c7f0d845a4",
                              |         "reference":"1097172564K",
                              |         "amountInPence":10623,
                              |         "status":"Created",
                              |         "createdOn":"2019-11-25T13:09:24.188",
                              |         "taxType":"self-assessment"
                              |      }
                              |   ]
                              |}
                              |
                              |""".stripMargin)

      when(http.GET[HttpResponse](any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(json))))

      connector.findPayments("111111111").futureValue shouldBe Some(json.as[PaymentSearchResult])
    }

    "parse json payload for multiple payments" in {

      val json = Json.parse("""
                              |{
                              |   "searchScope":"PTA",
                              |   "searchTag":"1097172564",
                              |   "payments":[
                              |      {
                              |         "id":"5ddbd2847a0000c7f0d845a4",
                              |         "reference":"1097172564K",
                              |         "amountInPence":10623,
                              |         "status":"Created",
                              |         "createdOn":"2019-11-25T13:09:24.188",
                              |         "taxType":"self-assessment"
                              |      },
                              |      {
                              |         "id":"5ddbd38f7a0000c7f0d845a5",
                              |         "reference":"1097172564K",
                              |         "amountInPence":20000,
                              |         "status":"Created",
                              |         "createdOn":"2019-11-25T13:13:51.755",
                              |         "taxType":"self-assessment"
                              |      }
                              |   ]
                              |}
                              |
                              |""".stripMargin)

      when(http.GET[HttpResponse](any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(json))))

      connector.findPayments("111111111").futureValue shouldBe Some(json.as[PaymentSearchResult])
    }

    "Returns a None when the status code is not OK" in {

      when(http.GET[HttpResponse](any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST)))

      connector.findPayments("111111111").futureValue shouldBe None
    }

    "Throws a JsResultException when bad json is returned by pay-api" in {

      val json = Json.parse("""{"testKey": "testValue"}""")

      when(http.GET[HttpResponse](any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Some(json))))

      val f = connector.findPayments("111111111")
      whenReady(f.failed) { e =>
        e shouldBe a[JsResultException]
      }
    }
  }
}
