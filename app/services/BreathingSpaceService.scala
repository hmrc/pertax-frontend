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
import models.BreathingSpaceIndicatorResponse
import play.api.http.Status._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.UpstreamErrorResponse.WithStatusCode
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class BreathingSpaceService @Inject() (
  configDecorator: ConfigDecorator,
  breathingSpaceConnector: BreathingSpaceConnector
) {

  def getBreathingSpaceIndicator(
    ninoOpt: Option[Nino]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[BreathingSpaceIndicatorResponse] =
    if (configDecorator.isBreathingSpaceIndicatorEnabled) {
      ninoOpt match {
        case Some(nino) =>
          breathingSpaceConnector
            .getBreathingSpaceIndicator(nino)
            .bimap(
              errorResponse => breathingSpaceIndicatorResponseForErrorResponse(errorResponse),
              breathingSpaceIndicator => BreathingSpaceIndicatorResponse.fromBoolean(breathingSpaceIndicator)
            )
            .merge
            .recover { case _ => BreathingSpaceIndicatorResponse.StatusUnknown }
        case _ => Future.successful(BreathingSpaceIndicatorResponse.StatusUnknown)
      }
    } else {
      Future.successful(BreathingSpaceIndicatorResponse.StatusUnknown)
    }

  private def breathingSpaceIndicatorResponseForErrorResponse(
    error: UpstreamErrorResponse
  ): BreathingSpaceIndicatorResponse =
    error match {
      case WithStatusCode(NOT_FOUND) => BreathingSpaceIndicatorResponse.NotFound
      case _                         => BreathingSpaceIndicatorResponse.StatusUnknown
    }
}
