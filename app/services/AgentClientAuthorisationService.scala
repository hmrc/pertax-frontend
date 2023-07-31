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

package services

import cats.implicits._
import com.google.inject.Inject
import connectors.AgentClientAuthorisationConnector
import models.AgentClientStatus
import models.admin.AgentClientAuthorisationToggle
import play.api.Logging
import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import util.{FutureEarlyTimeout, RateLimitedException}

import scala.concurrent.{ExecutionContext, Future}

class AgentClientAuthorisationService @Inject() (
  agentClientAuthorisationConnector: AgentClientAuthorisationConnector,
  featureFlagService: FeatureFlagService
) extends Logging {

  def getAgentClientStatus(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[Boolean] =
    featureFlagService.get(AgentClientAuthorisationToggle).flatMap { toggle =>
      if (toggle.isEnabled) {
        agentClientAuthorisationConnector.getAgentClientStatus
          .fold(
            _ => false,
            {
              case AgentClientStatus(false, false, false) => false
              case _                                      => true
            }
          )
          .recover {
            case FutureEarlyTimeout   =>
              false
            case RateLimitedException => false
          }
      } else {
        Future.successful(false)
      }
    }
}
