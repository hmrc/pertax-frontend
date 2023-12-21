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
import play.api.Application
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}

import scala.util.Random

class BreathingSpaceConnectorSpec extends ConnectorSpec with WireMockHelper {

  override implicit lazy val app: Application = app(
    Map("microservice.services.breathing-space-if-proxy.port" -> server.port())
  )

  val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)
  val url        = s"/$nino/memorandum"

  def connector: BreathingSpaceConnector = app.injector.instanceOf[BreathingSpaceConnector]

  def verifyHeader(requestPattern: RequestPatternBuilder): Unit = server.verify(
    requestPattern.withHeader(
      "Correlation-Id",
      matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
    )
  )

  val breathingSpaceTrueResponse: String =
    s"""
       |{
       |    "breathingSpaceIndicator": true
       |}
    """.stripMargin

  val breathingSpaceFalseResponse: String =
    s"""
       |{
       |    "breathingSpaceIndicator": false
       |}
    """.stripMargin

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
      s"return an UpstreamErrorResponse when $httpResponse status is received" in {
        stubGet(url, httpResponse, None)

        val result = connector.getBreathingSpaceIndicator(nino).value.futureValue
        result mustBe a[Left[UpstreamErrorResponse, _]]
        verifyHeader(getRequestedFor(urlEqualTo(url)))
      }
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
