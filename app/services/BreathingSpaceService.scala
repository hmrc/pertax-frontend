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
import config.ConfigDecorator
import connectors.BreathingSpaceConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import util.{FutureEarlyTimeout, RateLimitedException}

import scala.concurrent.{ExecutionContext, Future}

class BreathingSpaceService @Inject() (
  configDecorator: ConfigDecorator,
  breathingSpaceConnector: BreathingSpaceConnector
) {

  def getBreathingSpaceIndicator(
    ninoOpt: Option[Nino]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    if (configDecorator.isBreathingSpaceIndicatorEnabled) {
      ninoOpt match {
        case Some(nino) =>
          breathingSpaceConnector
            .getBreathingSpaceIndicator(nino)
            .bimap(_ => false, breathingSpaceIndicator => breathingSpaceIndicator)
            .merge
            .recover {
              case FutureEarlyTimeout   => false
              case RateLimitedException => false
            }
        case _ => Future.successful(false)
      }
    } else {
      Future.successful(false)
    }
}
