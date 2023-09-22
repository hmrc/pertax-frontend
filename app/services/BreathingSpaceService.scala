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
import connectors.BreathingSpaceConnector
import models.BreathingSpaceIndicatorResponse
import models.admin.BreathingSpaceIndicatorToggle
import play.api.http.Status._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.UpstreamErrorResponse.WithStatusCode
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import scala.concurrent.{ExecutionContext, Future}

class BreathingSpaceService @Inject() (
  breathingSpaceConnector: BreathingSpaceConnector,
  featureFlagService: FeatureFlagService
) {

  def getBreathingSpaceIndicator(
    ninoOpt: Option[Nino]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[BreathingSpaceIndicatorResponse] =
    featureFlagService.get(BreathingSpaceIndicatorToggle).flatMap { toggle =>
      if (toggle.isEnabled) {
        ninoOpt match {
          case Some(nino) =>
            breathingSpaceConnector
              .getBreathingSpaceIndicator(nino)
              .fold(
                errorResponse => breathingSpaceIndicatorResponseForErrorResponse(errorResponse),
                breathingSpaceIndicator => BreathingSpaceIndicatorResponse.fromBoolean(breathingSpaceIndicator)
              )
              .recover { case _ => BreathingSpaceIndicatorResponse.StatusUnknown }
          case _          => Future.successful(BreathingSpaceIndicatorResponse.StatusUnknown)
        }
      } else {
        Future.successful(BreathingSpaceIndicatorResponse.StatusUnknown)
      }
    }

  private def breathingSpaceIndicatorResponseForErrorResponse(
    error: UpstreamErrorResponse
  ): BreathingSpaceIndicatorResponse =
    error match {
      case WithStatusCode(NOT_FOUND) => BreathingSpaceIndicatorResponse.NotFound
      case _                         => BreathingSpaceIndicatorResponse.StatusUnknown
    }
}
