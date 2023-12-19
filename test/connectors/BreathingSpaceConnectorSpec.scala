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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import config.ConfigDecorator
import org.mockito.{ArgumentMatchers, Mockito, MockitoSugar}
import play.api.{Application, Logger}
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}

import scala.util.Random

class BreathingSpaceConnectorSpec extends ConnectorSpec with WireMockHelper with MockitoSugar {

  override implicit lazy val app: Application = app(
    Map("microservice.services.breathing-space-if-proxy.port" -> server.port())
  )

  private val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)
  private val url        = s"/$nino/memorandum"

  private def connector: BreathingSpaceConnector = app.injector.instanceOf[BreathingSpaceConnector]

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

  private def httpClientV2: HttpClientV2       = app.injector.instanceOf[HttpClientV2]
  private def configDecorator: ConfigDecorator = app.injector.instanceOf[ConfigDecorator]

  private val mockLogger: Logger = mock[Logger]

  def httpClientResponseUsingMockLogger: HttpClientResponse = new HttpClientResponse {
    override protected val logger: Logger = mockLogger
  }

  "getBreathingSpaceIndicator is called" must {
    "return a true right response" in {
      stubGet(url, OK, Some(breathingSpaceTrueResponse))

      val result = connector.getBreathingSpaceIndicator(nino).value.futureValue
      result mustBe a[Right[_, _]]
      result.getOrElse(HttpResponse(OK, "")) mustBe true
      verifyHeader(getRequestedFor(urlEqualTo(url)))
    }

    "return a false right response" in {
      stubGet(url, OK, Some(breathingSpaceFalseResponse))

      val result = connector.getBreathingSpaceIndicator(nino).value.futureValue
      result mustBe a[Right[_, _]]
      result.getOrElse(HttpResponse(OK, "")) mustBe false
      verifyHeader(getRequestedFor(urlEqualTo(url)))
    }

    "return an UpstreamErrorResponse for timeout response" in {
      val delay: Int = 6000
      stubWithDelay(url, OK, None, Some(breathingSpaceTrueResponse), delay)

      val result = connector.getBreathingSpaceIndicator(nino).value.futureValue
      result mustBe a[Left[UpstreamErrorResponse, _]]
      verifyHeader(getRequestedFor(urlEqualTo(url)))
    }

    List(
      TOO_MANY_REQUESTS,
      INTERNAL_SERVER_ERROR,
      BAD_GATEWAY,
      SERVICE_UNAVAILABLE,
      IM_A_TEAPOT,
      NOT_FOUND,
      BAD_REQUEST,
      UNPROCESSABLE_ENTITY
    ).foreach { httpResponse =>
      s"return an UpstreamErrorResponse when $httpResponse status is received and log 0 warnings & 1 error" in {
        reset(mockLogger)
        val connector = new BreathingSpaceConnector(httpClientV2, httpClientResponseUsingMockLogger, configDecorator) {}
        doNothing.when(mockLogger).warn(ArgumentMatchers.any())(ArgumentMatchers.any())
        doNothing.when(mockLogger).error(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
        stubGet(url, httpResponse, None)

        val result = connector.getBreathingSpaceIndicator(nino).value.futureValue
        result mustBe a[Left[UpstreamErrorResponse, _]]
        verifyHeader(getRequestedFor(urlEqualTo(url)))
        Mockito
          .verify(mockLogger, times(0))
          .warn(ArgumentMatchers.any())(ArgumentMatchers.any())
        Mockito
          .verify(mockLogger, times(1))
          .error(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
      }
    }

    "return Left but 1 warning logged & no errors if receive 401 (indicates IF being restarted due to new release)" in {
      reset(mockLogger)

      val connector = new BreathingSpaceConnector(httpClientV2, httpClientResponseUsingMockLogger, configDecorator) {}
      doNothing.when(mockLogger).warn(ArgumentMatchers.any())(ArgumentMatchers.any())
      doNothing.when(mockLogger).error(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())

      stubGet(
        url,
        UNAUTHORIZED,
        Some("""{"errors":[{"code":"BREATHING_SPACE_EXPIRED","message":
            |"Breathing Space has expired for the given Nino"}]}""".stripMargin)
      )

      val result = connector.getBreathingSpaceIndicator(nino).value.futureValue
      result mustBe a[Left[UpstreamErrorResponse, _]]
      result.swap.exists(_.statusCode == UNAUTHORIZED)
      verifyHeader(getRequestedFor(urlEqualTo(url)))

      Mockito
        .verify(mockLogger, times(1))
        .warn(ArgumentMatchers.any())(ArgumentMatchers.any())
      Mockito
        .verify(mockLogger, times(0))
        .error(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())
    }
  }

}
