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

import cats.data.EitherT
import com.github.tomakehurst.wiremock.client.WireMock.{get, serverError, urlEqualTo}
import play.api.inject.bind
import models.AgentClientStatus
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.concurrent.IntegrationPatience
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import repositories.SessionCacheRepository
import uk.gov.hmrc.http.UpstreamErrorResponse
import testUtils.BaseSpec
import cats.implicits._
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.Future

class CachingAgentClientAuthorisationConnectorSpec extends BaseSpec with WireMockHelper with IntegrationPatience {
  val mockAgentClientAuthorisationConnector: AgentClientAuthorisationConnector =
    mock[AgentClientAuthorisationConnector]
  val mockSessionCacheRepository: SessionCacheRepository = mock[SessionCacheRepository]

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      bind(classOf[AgentClientAuthorisationConnector])
        .qualifiedWith("default")
        .toInstance(mockAgentClientAuthorisationConnector),
      bind[SessionCacheRepository].toInstance(mockSessionCacheRepository)
    )
    .configure(
      "microservice.services.agent-client-authorisation.port" -> server.port()
    )
    .build()

  override def beforeEach: Unit =
    reset(mockAgentClientAuthorisationConnector, mockSessionCacheRepository)

  def sut: CachingAgentClientAuthorisationConnector = injected[CachingAgentClientAuthorisationConnector]

  val url = "/agent-client-authorisation/status"

  implicit val userRequest = FakeRequest()

  "Calling CachingAgentClientAuthorisationConnector.getAgentClientStatus" must {
    "return a Right AgentClientStatus object" when {
      "no value is cached" in {
        val expected = AgentClientStatus(true, true, true)
        when(mockSessionCacheRepository.getFromSession[AgentClientStatus](DataKey(any[String]()))(any(), any()))
          .thenReturn(
            Future.successful(None)
          )
        when(
          mockSessionCacheRepository.putSession[AgentClientStatus](DataKey(any[String]()), any())(any(), any(), any())
        )
          .thenReturn(
            Future.successful(("", ""))
          )

        when(mockAgentClientAuthorisationConnector.getAgentClientStatus).thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](expected)
        )

        val result = sut.getAgentClientStatus.value.futureValue

        result mustBe Right(expected)
        verify(mockSessionCacheRepository, times(1))
          .getFromSession[AgentClientStatus](DataKey(any[String]()))(any(), any())
        verify(mockSessionCacheRepository, times(1))
          .putSession[AgentClientStatus](DataKey(any[String]()), any())(any(), any(), any())
        verify(mockAgentClientAuthorisationConnector, times(1)).getAgentClientStatus
      }

      "a value is cached" in {
        val expected = AgentClientStatus(true, true, true)
        when(mockSessionCacheRepository.getFromSession[AgentClientStatus](DataKey(any[String]()))(any(), any()))
          .thenReturn(
            Future.successful(Some(expected))
          )

        when(mockAgentClientAuthorisationConnector.getAgentClientStatus).thenReturn(
          null
        )
        when(
          mockSessionCacheRepository.putSession[AgentClientStatus](DataKey(any[String]()), any())(any(), any(), any())
        )
          .thenReturn(
            null
          )

        val result = sut.getAgentClientStatus.value.futureValue

        result mustBe Right(expected)
        verify(mockSessionCacheRepository, times(1))
          .getFromSession[AgentClientStatus](DataKey(any[String]()))(any(), any())
        verify(mockSessionCacheRepository, times(0))
          .putSession[AgentClientStatus](DataKey(any[String]()), any())(any(), any(), any())
        verify(mockAgentClientAuthorisationConnector, times(0)).getAgentClientStatus
      }
    }

    "return a Left UpstreamErrorResponse object" ignore {
      server.stubFor(
        get(urlEqualTo(url)).willReturn(serverError)
      )

      val result = sut.getAgentClientStatus.value.futureValue

      result.left.get mustBe a[UpstreamErrorResponse]
    }

  }
}
