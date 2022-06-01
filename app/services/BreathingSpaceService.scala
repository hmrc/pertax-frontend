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

package services

import cats.implicits._
import com.google.inject.Inject
import connectors.{AgentClientAuthorisationConnector, BreathingSpaceConnector}
import models.AgentClientStatus
import play.api.mvc.Request
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.{FutureEarlyTimeout, RateLimitedException}

import scala.concurrent.{ExecutionContext, Future}

class BreathingSpaceService @Inject()(
  servicesConfig: ServicesConfig,
  breathingSpaceConnector: BreathingSpaceConnector
) {

  lazy private val breathingSpaceIndicatorEnabled =
    servicesConfig.getBoolean("feature.breathing-Space-indicator.enabled")

  def getBreathingSpaceIndicator(ninoOpt: Option[Nino])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
     {
      breathingSpaceConnector.getBreathingSpaceIndicator(ninoOpt.get)
        .bimap(
          _ => false, breathingSpaceIndicator => breathingSpaceIndicator)
        .merge
        .recover {
          case FutureEarlyTimeout   => false
          case RateLimitedException => false
        }
    } else {
      Future.successful(false)
    })
}
