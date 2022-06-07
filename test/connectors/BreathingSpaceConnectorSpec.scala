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
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino
import util.{BaseSpec, Fixtures, WireMockHelper}

class BreathingSpaceConnectorSpec extends BaseSpec with WireMockHelper {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.breathing-space-if-proxy.port" -> server.port()
    )
    .build()

  def sut: BreathingSpaceConnector = injected[BreathingSpaceConnector]
  val nino: Nino = Fixtures.fakeNino

  val url = s"/$nino/memorandum"

  "BreathingSpaceConnector getBreathingSpaceIndicator is called" must {

    "return a right response" in {

      server.stubFor(
        get(urlPathEqualTo(url))
          .willReturn(
            okJson(
              Json
                .toJson(true)
                .toString()
            )
          )
      )

      println(
        "sut\n        .getBreathingSpaceIndicator(nino)\n        .value\n        .futureValue.." +
          sut
            .getBreathingSpaceIndicator(nino)
            .value
            .futureValue
      )

      sut
        .getBreathingSpaceIndicator(nino)
        .value
        .futureValue mustBe Right(true)
    }
  }

}
