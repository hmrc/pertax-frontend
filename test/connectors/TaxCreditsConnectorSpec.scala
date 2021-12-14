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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import util.Fixtures.fakeNino
import util.{BaseSpec, FileHelper, NullMetrics, WireMockHelper}

class TaxCreditsConnectorSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  lazy val http = app.injector.instanceOf[DefaultHttpClient]

  def connector: TaxCreditsConnector = new TaxCreditsConnector(http, config, new NullMetrics)

  lazy val url: String = s"/tcs/$fakeNino/dashboard-data"

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.tcs-broker.port" -> server.port()
    )
    .build()

  "TaxCreditsConnector" when {
    "checkForTaxCredits is called" must {
      "return a HttpResponse containing OK if tcs data for the given NINO is found" in {

        server.stubFor(
          get(urlEqualTo(url)).willReturn(ok(FileHelper.loadFile("./test/resources/tcs/dashboard-data.json")))
        )

        val result = connector.checkForTaxCredits(fakeNino).value.futureValue.right.get

        result.status mustBe OK
      }

      "return a UpstreamErrorException containing NOT_FOUND if tcs data for the given isn't found" in {

        server.stubFor(
          get(urlEqualTo(url)).willReturn(aResponse().withStatus(NOT_FOUND))
        )

        val result = connector.checkForTaxCredits(fakeNino).value.futureValue.left.get

        result.statusCode mustBe NOT_FOUND
      }

      List(
        BAD_REQUEST,
        IM_A_TEAPOT,
        INTERNAL_SERVER_ERROR,
        SERVICE_UNAVAILABLE
      ).foreach { status =>
        s"return an UpstreamErrorException containing INTERNAL_SERVER_ERROR when $status is returned from TCS Broker" in {

          server.stubFor(
            get(urlEqualTo(url)).willReturn(aResponse().withStatus(status))
          )

          val result = connector.checkForTaxCredits(fakeNino).value.futureValue.left.get

          result.statusCode mustBe status
        }
      }
    }
  }
}
