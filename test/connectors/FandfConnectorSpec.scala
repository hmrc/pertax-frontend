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

import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import testUtils.WireMockHelper
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.UpstreamErrorResponse
import play.api.libs.json.Json

import scala.util.Random

class FandfConnectorSpec extends ConnectorSpec with WireMockHelper with MockitoSugar {

  override implicit lazy val app: Application = app(
    Map("microservice.services.fandf.port" -> server.port())
  )

  private val nino: Nino = Nino(new Generator(new Random()).nextNino.nino)
  private val url        = s"/fandf/$nino/showBanner"

  private def connector: FandFConnector = app.injector.instanceOf[FandFConnector]

  "showFandfBanner is called" must {
    "return a true right response" in {
      stubGet(url, OK, Some(Json.toJson(true).toString))

      val result = connector.showFandfBanner(nino).value.futureValue
      result mustBe a[Right[_, Boolean]]
      result.getOrElse(false) mustBe true
    }

    "return a false right response" in {
      stubGet(url, OK, Some(Json.toJson(false).toString))

      val result = connector.showFandfBanner(nino).value.futureValue
      result mustBe a[Right[_, Boolean]]
      result.getOrElse(true) mustBe false
    }

    List(SERVICE_UNAVAILABLE, IM_A_TEAPOT, BAD_REQUEST).foreach { httpResponse =>
      s"return an UpstreamErrorResponse when $httpResponse status is received" in {
        stubGet(url, httpResponse, Some("nonsense response"))

        val result = connector.showFandfBanner(nino).value.futureValue
        result mustBe a[Left[UpstreamErrorResponse, _]]
      }
    }
  }
}
