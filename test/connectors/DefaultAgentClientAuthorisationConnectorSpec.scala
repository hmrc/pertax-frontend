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

import com.github.tomakehurst.wiremock.client.WireMock.{ok, urlEqualTo}
import models.AgentClientStatus
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import testUtils.BaseSpec
import com.github.tomakehurst.wiremock.client.WireMock._
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http.UpstreamErrorResponse

class DefaultAgentClientAuthorisationConnectorSpec extends BaseSpec with WireMockHelper with IntegrationPatience {
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.agent-client-authorisation.port" -> server.port()
    )
    .build()

  def sut: DefaultAgentClientAuthorisationConnector = injected[DefaultAgentClientAuthorisationConnector]

  val url = "/agent-client-authorisation/status"

  implicit val userRequest = FakeRequest()

  "Calling DefaultAgentClientAuthorisationConnector.getAgentClientStatus" must {
    "return a Right AgentClientStatus object" in {
      val expected = AgentClientStatus(true, true, true)
      val response = Json.toJson(expected).toString

      server.stubFor(
        get(urlEqualTo(url)).willReturn(ok(response))
      )

      val result = sut.getAgentClientStatus.value.futureValue

      result mustBe Right(expected)
    }

    "return a Left UpstreamErrorResponse object" in {
      server.stubFor(
        get(urlEqualTo(url)).willReturn(serverError)
      )

      val result = sut.getAgentClientStatus.value.futureValue

      result.left.get mustBe a[UpstreamErrorResponse]
    }
  }
}
