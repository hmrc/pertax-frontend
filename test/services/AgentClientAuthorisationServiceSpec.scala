/*
 * Copyright 2025 HM Revenue & Customs
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

package services

import cats.data.EitherT
import connectors.AgentClientAuthorisationConnector
import controllers.auth.requests.UserRequest
import models.*
import models.admin.AgentClientRelationshipsToggle
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import testUtils.BaseSpec
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import util.{FutureEarlyTimeout, RateLimitedException}

import scala.concurrent.Future

class AgentClientAuthorisationServiceSpec extends BaseSpec {

  private val mockAgentClientAuthorisationConnector: AgentClientAuthorisationConnector =
    mock[AgentClientAuthorisationConnector]
  private val mockFeatureFlagService: FeatureFlagService                               = mock[FeatureFlagService]

  private lazy val service: AgentClientAuthorisationService =
    new AgentClientAuthorisationService(mockAgentClientAuthorisationConnector, mockFeatureFlagService)
  implicit lazy val messages: Messages                      = MessagesImpl(Lang("en"), messagesApi)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFeatureFlagService)
    reset(mockAgentClientAuthorisationConnector)
  }

  "getAgentClientStatus" when {
    "AgentClientRelationshipsToggle is enabled and not using trusted helper" must {
      "return true" when {
        val combinations: List[(Boolean, Boolean, Boolean)] =
          for {
            a <- List(true, false)
            b <- List(true, false)
            c <- List(true, false)
            if a || b || c
          } yield (a, b, c)

        combinations.foreach { combination =>
          s"agent statuses are $combination" in {

            implicit val request: UserRequest[AnyContent] = UserRequest(
              generatedNino,
              NonFilerSelfAssessmentUser,
              Credentials("credId", "GovernmentGateway"),
              ConfidenceLevel.L200,
              None,
              Set.empty,
              None,
              None,
              FakeRequest(),
              UserAnswers.empty
            )
            when(mockFeatureFlagService.get(ArgumentMatchers.eq(AgentClientRelationshipsToggle)))
              .thenReturn(Future.successful(FeatureFlag(AgentClientRelationshipsToggle, true)))
            when(mockAgentClientAuthorisationConnector.getAgentClientStatus(any(), any(), any())).thenReturn(
              EitherT.rightT[Future, UpstreamErrorResponse](
                AgentClientStatus(
                  hasPendingInvitations = combination._1,
                  hasInvitationsHistory = combination._2,
                  hasExistingRelationships = combination._3
                )
              )
            )

            val result = service.getAgentClientStatus.futureValue

            result mustBe true

          }
        }

        "return false" when {
          "agent statuses are (false, false, false)" in {

            implicit val request: UserRequest[AnyContent] = UserRequest(
              generatedNino,
              NonFilerSelfAssessmentUser,
              Credentials("credId", "GovernmentGateway"),
              ConfidenceLevel.L200,
              None,
              Set.empty,
              None,
              None,
              FakeRequest(),
              UserAnswers.empty
            )
            when(mockFeatureFlagService.get(ArgumentMatchers.eq(AgentClientRelationshipsToggle)))
              .thenReturn(Future.successful(FeatureFlag(AgentClientRelationshipsToggle, true)))
            when(mockAgentClientAuthorisationConnector.getAgentClientStatus(any(), any(), any())).thenReturn(
              EitherT.rightT[Future, UpstreamErrorResponse](
                AgentClientStatus(
                  hasPendingInvitations = false,
                  hasInvitationsHistory = false,
                  hasExistingRelationships = false
                )
              )
            )

            val result = service.getAgentClientStatus.futureValue

            result mustBe false

          }

          "AgentClientRelationshipsToggle is disabled" in {

            implicit val request: UserRequest[AnyContent] = UserRequest(
              generatedNino,
              NonFilerSelfAssessmentUser,
              Credentials("credId", "GovernmentGateway"),
              ConfidenceLevel.L200,
              None,
              Set.empty,
              None,
              None,
              FakeRequest(),
              UserAnswers.empty
            )
            when(mockFeatureFlagService.get(ArgumentMatchers.eq(AgentClientRelationshipsToggle)))
              .thenReturn(Future.successful(FeatureFlag(AgentClientRelationshipsToggle, false)))
            when(mockAgentClientAuthorisationConnector.getAgentClientStatus(any(), any(), any())).thenReturn(
              EitherT.rightT[Future, UpstreamErrorResponse](
                AgentClientStatus(
                  hasPendingInvitations = true,
                  hasInvitationsHistory = false,
                  hasExistingRelationships = false
                )
              )
            )

            val result = service.getAgentClientStatus.futureValue

            result mustBe false

          }

          "trusted helper is enabled" in {

            implicit val request: UserRequest[AnyContent] = UserRequest(
              generatedNino,
              NonFilerSelfAssessmentUser,
              Credentials("credId", "GovernmentGateway"),
              ConfidenceLevel.L200,
              Some(TrustedHelper("principalName", "attorneyName", "returnLinkUrl", None)),
              Set.empty,
              None,
              None,
              FakeRequest(),
              UserAnswers.empty
            )
            when(mockFeatureFlagService.get(ArgumentMatchers.eq(AgentClientRelationshipsToggle)))
              .thenReturn(Future.successful(FeatureFlag(AgentClientRelationshipsToggle, false)))
            when(mockAgentClientAuthorisationConnector.getAgentClientStatus(any(), any(), any())).thenReturn(
              EitherT.rightT[Future, UpstreamErrorResponse](
                AgentClientStatus(
                  hasPendingInvitations = true,
                  hasInvitationsHistory = false,
                  hasExistingRelationships = false
                )
              )
            )

            val result = service.getAgentClientStatus.futureValue

            result mustBe false

          }

          "An error is returned by the connector" in {
            implicit val request: UserRequest[AnyContent] = UserRequest(
              generatedNino,
              NonFilerSelfAssessmentUser,
              Credentials("credId", "GovernmentGateway"),
              ConfidenceLevel.L200,
              None,
              Set.empty,
              None,
              None,
              FakeRequest(),
              UserAnswers.empty
            )
            when(mockFeatureFlagService.get(ArgumentMatchers.eq(AgentClientRelationshipsToggle)))
              .thenReturn(Future.successful(FeatureFlag(AgentClientRelationshipsToggle, true)))
            when(mockAgentClientAuthorisationConnector.getAgentClientStatus(any(), any(), any())).thenReturn(
              EitherT.leftT[Future, AgentClientStatus](
                UpstreamErrorResponse("error", 500)
              )
            )

            val result = service.getAgentClientStatus.futureValue

            result mustBe false
          }

          "the rate limit is breached" in {
            implicit val request: UserRequest[AnyContent] = UserRequest(
              generatedNino,
              NonFilerSelfAssessmentUser,
              Credentials("credId", "GovernmentGateway"),
              ConfidenceLevel.L200,
              None,
              Set.empty,
              None,
              None,
              FakeRequest(),
              UserAnswers.empty
            )
            when(mockFeatureFlagService.get(ArgumentMatchers.eq(AgentClientRelationshipsToggle)))
              .thenReturn(Future.successful(FeatureFlag(AgentClientRelationshipsToggle, true)))
            when(mockAgentClientAuthorisationConnector.getAgentClientStatus(any(), any(), any())).thenThrow(
              RateLimitedException
            )

            val result = intercept[RateLimitedException.type](
              await(service.getAgentClientStatus)
            )

            result mustBe RateLimitedException
          }

          "the call timed out" in {
            implicit val request: UserRequest[AnyContent] = UserRequest(
              generatedNino,
              NonFilerSelfAssessmentUser,
              Credentials("credId", "GovernmentGateway"),
              ConfidenceLevel.L200,
              None,
              Set.empty,
              None,
              None,
              FakeRequest(),
              UserAnswers.empty
            )
            when(mockFeatureFlagService.get(ArgumentMatchers.eq(AgentClientRelationshipsToggle)))
              .thenReturn(Future.successful(FeatureFlag(AgentClientRelationshipsToggle, true)))
            when(mockAgentClientAuthorisationConnector.getAgentClientStatus(any(), any(), any())).thenThrow(
              FutureEarlyTimeout
            )

            val result = intercept[FutureEarlyTimeout.type](
              await(service.getAgentClientStatus)
            )

            result mustBe FutureEarlyTimeout
          }

        }
      }
    }
  }
}
