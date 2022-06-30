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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{BAD_GATEWAY, BAD_REQUEST, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, NOT_FOUND, SERVICE_UNAVAILABLE, UNPROCESSABLE_ENTITY}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.UpstreamErrorResponse
import util.{BaseSpec, Fixtures, WireMockHelper}

class BreathingSpaceConnectorSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.breathing-space-if-proxy.port" -> server.port()
    )
    .build()

  def sut: BreathingSpaceConnector = injected[BreathingSpaceConnector]
  val nino: Nino = Fixtures.fakeNino

  val url = s"/$nino/memorandum"

  def verifyHeader(requestPattern: RequestPatternBuilder): Unit =
    server.verify(
      requestPattern
        .withHeader(
          "Correlation-Id",
          matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
        )
    )

  val breathingSpaceTrueResponse =
    s"""
       |{
       |    "breathingSpaceIndicator": true
       |}
       |""".stripMargin

  val breathingSpaceFalseResponse =
    s"""
       |{
       |    "breathingSpaceIndicator": false
       |}
       |""".stripMargin

  "getBreathingSpaceIndicator is called" must {

    "return a true right response" in {

      server.stubFor(
        get(urlPathEqualTo(url))
          .willReturn(ok(breathingSpaceTrueResponse))
      )

      sut
        .getBreathingSpaceIndicator(nino)
        .value
        .futureValue
        .right
        .get mustBe true

      verifyHeader(getRequestedFor(urlEqualTo(url)))
    }

    "return a false right response" in {

      server.stubFor(
        get(urlPathEqualTo(url))
          .willReturn(ok(breathingSpaceFalseResponse))
      )

      sut
        .getBreathingSpaceIndicator(nino)
        .value
        .futureValue
        .right
        .get mustBe false

      verifyHeader(getRequestedFor(urlEqualTo(url)))
    }

    "return a BAD_GATEWAY for timeout response" in {

      server.stubFor(
        get(urlPathEqualTo(url))
          .willReturn(ok(breathingSpaceTrueResponse).withFixedDelay(5000))
      )

      val result = sut
        .getBreathingSpaceIndicator(nino)
        .value
        .futureValue
        .left
        .get

      result mustBe an[UpstreamErrorResponse]
      result.statusCode mustBe BAD_GATEWAY

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
      s"return a $httpResponse when $httpResponse status is received" in {

        server.stubFor(
          get(urlPathEqualTo(url))
            .willReturn(aResponse.withStatus(httpResponse))
        )

        val result = sut
          .getBreathingSpaceIndicator(nino)
          .value
          .futureValue
          .left
          .get

        result mustBe an[UpstreamErrorResponse]
        result.statusCode mustBe httpResponse
        verifyHeader(getRequestedFor(urlEqualTo(url)))
      }
    }

  }

}
