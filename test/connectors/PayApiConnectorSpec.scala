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

import cats.data.EitherT
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post}
import config.ConfigDecorator
import izumi.reflect.Tag
import models.{PayApiModels, PaymentRequest}
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.libs.json.Json
import play.api.libs.ws.BodyWritable
import play.api.test.{DefaultAwaitTimeout, Injecting}
import testUtils.WireMockHelper
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class PayApiConnectorSpec extends ConnectorSpec with WireMockHelper with DefaultAwaitTimeout with Injecting {

  private val mockHttpClientResponse: HttpClientResponse = mock[HttpClientResponse]

  private val mockHttpClient: HttpClientV2 = mock[HttpClientV2]

  private val mockConfigDecorator = mock[ConfigDecorator]

  private val dummyContent = "error message"

  private val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

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
      REQUEST_TIMEOUT,
      SERVICE_UNAVAILABLE
    ).foreach { error =>
      s"Returns an UpstreamErrorResponse when the status code is $error" in {
        stubPost(url, error, Some(Json.toJson(paymentRequest).toString()), Some(json.toString()))
        val result =
          connector.createPayment(paymentRequest).value.futureValue.swap.getOrElse(UpstreamErrorResponse("", OK))

        result.statusCode mustBe error
      }
    }

    List(
      INTERNAL_SERVER_ERROR,
      BAD_GATEWAY,
      NOT_FOUND,
      UNPROCESSABLE_ENTITY
    ).foreach { httpResponse =>
      s"Returns an UpstreamErrorResponse when the status code is $httpResponse from HttpClientResponse" in {

        when(mockHttpClientResponse.read(any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, HttpResponse](
            Future(Left(UpstreamErrorResponse(dummyContent, httpResponse)))
          )
        )

        when(mockHttpClient.post(any())(any[HeaderCarrier])).thenReturn(mockRequestBuilder)

        when(mockRequestBuilder.withBody(any())(any[BodyWritable[Any]], any[Tag[Any]], any[ExecutionContext]))
          .thenReturn(mockRequestBuilder)

        when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any[ExecutionContext]))
          .thenReturn(Future.successful(HttpResponse(httpResponse, "")))

        when(mockConfigDecorator.makeAPaymentUrl).thenReturn("http://localhost:8080" + url)

        def payApiConnectorWithMock: PayApiConnector =
          new PayApiConnector(mockHttpClient, mockConfigDecorator, mockHttpClientResponse)

        val result =
          payApiConnectorWithMock
            .createPayment(paymentRequest)
            .value
            .futureValue
            .swap
            .getOrElse(UpstreamErrorResponse("", OK))

        result.statusCode mustBe httpResponse
      }
    }
  }
}
