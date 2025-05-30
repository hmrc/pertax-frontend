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
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import config.ConfigDecorator
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, ArgumentMatchers, Mockito}
import play.api.{Application, Logger}
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito.{mock, reset, times, when}

import scala.concurrent.Future
import scala.util.Random
import com.github.tomakehurst.wiremock.client.WireMock.{getRequestedFor, matching, reset as resetWireMock, urlEqualTo}

class BreathingSpaceConnectorSpec extends ConnectorSpec with WireMockHelper with MockitoSugar {

  override implicit lazy val app: Application = app(
    Map("microservice.services.breathing-space-if-proxy.port" -> server.port())
  )

  private val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)
  private val url        = s"/$nino/memorandum"

  private def connector: BreathingSpaceConnector = app.injector.instanceOf[BreathingSpaceConnector]

  private val mockHttpClientResponse: HttpClientResponse = mock[HttpClientResponse]

  private val mockHttpClientV2: HttpClientV2 = mock[HttpClientV2]

  private val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

  private val mockConfigDecorator = mock[ConfigDecorator]

  private def verifyHeader(requestPattern: RequestPatternBuilder): Unit = server.verify(
    requestPattern.withHeader(
      "Correlation-Id",
      matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
    )
  )

  private val breathingSpaceTrueResponse: String =
    s"""
       |{
       |    "breathingSpaceIndicator": true
       |}
    """.stripMargin

  private val breathingSpaceFalseResponse: String =
    s"""
       |{
       |    "breathingSpaceIndicator": false
       |}
    """.stripMargin

  private def httpClientV2: HttpClientV2 = app.injector.instanceOf[HttpClientV2]

  private def configDecorator: ConfigDecorator = app.injector.instanceOf[ConfigDecorator]

  private val mockLogger: Logger = mock[Logger]

  private val dummyContent = "dummy error response"

  private def httpClientResponseUsingMockLogger: HttpClientResponse = new HttpClientResponse {
    override protected val logger: Logger = mockLogger
  }

  "getBreathingSpaceIndicator is called" must {
    "return a true right response" in {
      stubGet(url, OK, Some(breathingSpaceTrueResponse))

      val result = connector.getBreathingSpaceIndicator(nino).value.futureValue
      result mustBe a[Right[_, Boolean]]
      result.getOrElse(false) mustBe true
      verifyHeader(getRequestedFor(urlEqualTo(url)))
    }

    "return a false right response" in {
      stubGet(url, OK, Some(breathingSpaceFalseResponse))

      val result = connector.getBreathingSpaceIndicator(nino).value.futureValue
      result mustBe a[Right[_, Boolean]]
      result.getOrElse(true) mustBe false
      verifyHeader(getRequestedFor(urlEqualTo(url)))
    }

    List(SERVICE_UNAVAILABLE, IM_A_TEAPOT, BAD_REQUEST).foreach { httpResponse =>
      s"return an UpstreamErrorResponse when $httpResponse status is received" in {
        reset(mockLogger)
        val connector = new BreathingSpaceConnector(httpClientV2, httpClientResponseUsingMockLogger, configDecorator) {}
        stubGet(url, httpResponse, Some(dummyContent))

        val result = connector.getBreathingSpaceIndicator(nino).value.futureValue
        result mustBe a[Left[UpstreamErrorResponse, _]]
        verifyHeader(getRequestedFor(urlEqualTo(url)))
      }
    }

    List(TOO_MANY_REQUESTS, INTERNAL_SERVER_ERROR, BAD_GATEWAY, NOT_FOUND, UNPROCESSABLE_ENTITY).foreach {
      httpResponse =>
        s"return an UpstreamErrorResponse from HttpClientResponse when $httpResponse status is received" in {

          when(mockHttpClientResponse.readLogForbiddenAsWarning(any())).thenReturn(
            EitherT[Future, UpstreamErrorResponse, HttpResponse](
              Future(Left(UpstreamErrorResponse(dummyContent, httpResponse)))
            )
          )

          when(mockHttpClientV2.get(any())(any())).thenReturn(mockRequestBuilder)

          when(mockRequestBuilder.transform(any()))
            .thenReturn(mockRequestBuilder)

          when(mockConfigDecorator.breathingSpaceBaseUrl).thenReturn("http://localhost:/bs")

          val connector = new BreathingSpaceConnector(mockHttpClientV2, mockHttpClientResponse, configDecorator)

          val result = connector.getBreathingSpaceIndicator(nino).value.futureValue
          result mustBe a[Left[UpstreamErrorResponse, _]]
        }
    }

    "return Left but 1 WARN level message logged if receive 403 (indicates IF being restarted due to new release)" in {
      reset(mockLogger)

      val connector = new BreathingSpaceConnector(httpClientV2, httpClientResponseUsingMockLogger, configDecorator) {}

      stubGet(
        url,
        FORBIDDEN,
        Some(dummyContent)
      )

      val result                              = connector.getBreathingSpaceIndicator(nino).value.futureValue
      result mustBe a[Left[UpstreamErrorResponse, _]]
      result.swap.exists(_.statusCode == FORBIDDEN)
      verifyHeader(getRequestedFor(urlEqualTo(url)))
      val eventCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      Mockito
        .verify(mockLogger, times(1))
        .warn(eventCaptor.capture())(ArgumentMatchers.any())
      eventCaptor.getValue.contains(dummyContent) mustBe true
      Mockito
        .verify(mockLogger, times(0))
        .error(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
    }
  }

}

class BreathingSpaceConnectorTimeoutSpec extends ConnectorSpec with WireMockHelper {

  override implicit lazy val app: Application = app(
    Map(
      "microservice.services.breathing-space-if-proxy.port"                  -> server.port(),
      "microservice.services.breathing-space-if-proxy.timeoutInMilliseconds" -> 1
    )
  )

  private val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)
  private val url        = s"/$nino/memorandum"

  def connector: BreathingSpaceConnector = app.injector.instanceOf[BreathingSpaceConnector]

  "getBreathingSpaceIndicator is called" must {
    "return a bad gateway response when connection times out" in {
      stubWithDelay(url, OK, None, None, 100)

      val result = connector.getBreathingSpaceIndicator(nino).value.futureValue
      result mustBe a[Left[UpstreamErrorResponse, _]]
      result.swap.getOrElse(UpstreamErrorResponse("", OK)).statusCode mustBe BAD_GATEWAY
    }
  }
}
