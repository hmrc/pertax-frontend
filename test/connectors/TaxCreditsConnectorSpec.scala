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
import play.api.http.Status.{BAD_REQUEST, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, NOT_FOUND, OK, SERVICE_UNAVAILABLE}
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import util.Fixtures.fakeNino
import util.{BaseSpec, FileHelper, NullMetrics, WireMockHelper}

class TaxCreditsConnectorSpec extends BaseSpec with WireMockHelper with IntegrationPatience {

  val fakeNino = Nino("AA000003A")

  lazy val http = app.injector.instanceOf[DefaultHttpClient]
  def connector = new TaxCreditsConnector(http, config, new NullMetrics)
  lazy val url: String = config.tcsBrokerHost + s"/tcs/$fakeNino/dashboard-data"

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.tcs-broker.port" -> 7901,
      "microservice.services.tcs-broker.host" -> "127.0.0.1"
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

      "return an UpstreamErrorException containing NOT_FOUND when BAD_REQUEST is returned from TCS Broker" in {

        val fakeNino = Nino("AA006435A")

        lazy val url: String = config.tcsBrokerHost + s"/tcs/$fakeNino/dashboard-data"

        server.stubFor(
          get(urlEqualTo(url)).willReturn(aResponse().withStatus(BAD_REQUEST))
        )

        val result = connector.checkForTaxCredits(fakeNino).value.futureValue.left.get

        result.statusCode mustBe NOT_FOUND
      }
    }

    List(
      NOT_FOUND,
      IM_A_TEAPOT,
      INTERNAL_SERVER_ERROR,
      SERVICE_UNAVAILABLE
    ).foreach { status =>
      s"return an UpstreamErrorException containing INTERNAL_SERVER_ERROR when $status is returned from TCS Broker" in {

        val fakeNino = Nino("AA006435A")

        lazy val url: String = config.tcsBrokerHost + s"/tcs/$fakeNino/dashboard-data"

        server.stubFor(
          get(urlEqualTo(url)).willReturn(aResponse().withStatus(status))
        )

        val result = connector.checkForTaxCredits(fakeNino).value.futureValue.left.get

        result.statusCode mustBe INTERNAL_SERVER_ERROR
      }
    }
  }

}
