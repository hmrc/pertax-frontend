/*
 * Copyright 2024 HM Revenue & Customs
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
import cats.implicits._
import models.{AgentClientStatus, UserAnswers}
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject.bind
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import repositories.JourneyCacheRepository
import routePages.AgentClientStatusPage
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class CachingAgentClientAuthorisationConnectorSpec extends ConnectorSpec with BaseSpec with WireMockHelper {

  val mockAgentClientAuthorisationConnector: AgentClientAuthorisationConnector = mock[AgentClientAuthorisationConnector]
  val mockJourneyCacheRepository: JourneyCacheRepository                       = mock[JourneyCacheRepository]

  override implicit val hc: HeaderCarrier         = HeaderCarrier()
  override implicit lazy val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  override implicit lazy val app: Application = app(
    Map("microservice.services.agent-client-authorisation.port" -> server.port()),
    bind(classOf[AgentClientAuthorisationConnector])
      .qualifiedWith("default")
      .toInstance(mockAgentClientAuthorisationConnector),
    bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository)
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAgentClientAuthorisationConnector, mockJourneyCacheRepository)
  }

  def connector: CachingAgentClientAuthorisationConnector = inject[CachingAgentClientAuthorisationConnector]
  val url                                                 = "/agent-client-authorisation/status"

  implicit val userRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  "Calling CachingAgentClientAuthorisationConnector.getAgentClientStatus" must {
    "return a Right AgentClientStatus object" when {
      "no value is cached" in {
        val expected = AgentClientStatus(
          hasPendingInvitations = true,
          hasInvitationsHistory = true,
          hasExistingRelationships = true
        )

        when(mockJourneyCacheRepository.get(any[HeaderCarrier]))
          .thenReturn(Future.successful(UserAnswers.empty("id")))

        when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))

        when(mockAgentClientAuthorisationConnector.getAgentClientStatus)
          .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](expected))

        val result = connector.getAgentClientStatus.value.futureValue

        result mustBe Right(expected)

        verify(mockJourneyCacheRepository, times(2)).get(any[HeaderCarrier])
        verify(mockJourneyCacheRepository, times(1)).set(any[UserAnswers])
        verify(mockAgentClientAuthorisationConnector, times(1)).getAgentClientStatus
      }

      "a value is cached" in {
        val expected = AgentClientStatus(
          hasPendingInvitations = true,
          hasInvitationsHistory = true,
          hasExistingRelationships = true
        )

        val userAnswers = UserAnswers.empty("id").setOrException(AgentClientStatusPage, expected)

        when(mockJourneyCacheRepository.get(any[HeaderCarrier]))
          .thenReturn(Future.successful(userAnswers))

        val result = connector.getAgentClientStatus.value.futureValue

        result mustBe Right(expected)

        verify(mockJourneyCacheRepository, times(1)).get(any[HeaderCarrier])
        verify(mockJourneyCacheRepository, times(0)).set(any[UserAnswers])
        verify(mockAgentClientAuthorisationConnector, times(0)).getAgentClientStatus
      }
    }

    "return a Left UpstreamErrorResponse object" ignore {
      stubGet(url, INTERNAL_SERVER_ERROR, None)

      val result = connector.getAgentClientStatus.value.futureValue
      result mustBe a[Left[_, _]]
      result.swap.getOrElse(UpstreamErrorResponse("", OK)) mustBe an[UpstreamErrorResponse]
    }
  }
}
