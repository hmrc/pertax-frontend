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

import models.AgentClientStatus
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.WireMockHelper
import uk.gov.hmrc.http.UpstreamErrorResponse

class DefaultAgentClientAuthorisationConnectorSpec extends ConnectorSpec with WireMockHelper with IntegrationPatience {

  override implicit lazy val app: Application = app(
    Map(
      "microservice.services.agent-client-authorisation.port" -> server.port(),
      "feature.agent-client-authorisation.maxTps"             -> 1000,
      "feature.agent-client-authorisation.cache"              -> true,
      "feature.agent-client-authorisation.timeoutInSec"       -> 1
    )
  )

  def connector: DefaultAgentClientAuthorisationConnector =
    app.injector.instanceOf[DefaultAgentClientAuthorisationConnector]

  val url = "/agent-client-authorisation/status"

  implicit val userRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  "Calling DefaultAgentClientAuthorisationConnector.getAgentClientStatus" must {
    "return a Right AgentClientStatus object" in {
      val expected = AgentClientStatus(
        hasPendingInvitations = true,
        hasInvitationsHistory = true,
        hasExistingRelationships = true
      )

      val response = Json.toJson(expected).toString

      stubGet(url, OK, Some(response))
      val result = connector.getAgentClientStatus.value.futureValue

      result mustBe Right(expected)
    }

    "return a Left UpstreamErrorResponse object" in {
      stubGet(url, INTERNAL_SERVER_ERROR, None)
      val result = connector.getAgentClientStatus.value.futureValue

      result mustBe a[Left[UpstreamErrorResponse, _]]
      result.swap.getOrElse(UpstreamErrorResponse("", OK)) mustBe a[UpstreamErrorResponse]
    }
  }
}
