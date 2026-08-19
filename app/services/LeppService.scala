/*
 * Copyright 2026 HM Revenue & Customs
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

import com.google.inject.Inject
import config.ConfigDecorator
import connectors.LeppConnector
import models.admin.LowEarnersPensionsPaymentToggle
import play.api.Logging
import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import scala.concurrent.{ExecutionContext, Future}

class LeppService @Inject() (
  leppConnector: LeppConnector,
  featureFlagService: FeatureFlagService,
  configDecorator: ConfigDecorator
)(implicit ec: ExecutionContext)
    extends Logging {

  def getLeppLink(implicit hc: HeaderCarrier, request: Request[?]): Future[Option[String]] =
    featureFlagService.get(LowEarnersPensionsPaymentToggle).flatMap { toggle =>
      if (toggle.isEnabled) {
        leppConnector.getLeppSummary
          .fold(_ => Option.empty[String], response => linkForStatus(response.status))
          .recover { case _ => None }
      } else {
        Future.successful(None)
      }
    }

  private def linkForStatus(status: String): Option[String] =
    status match {
      case "PAYMENTS_AVAILABLE" => Some(configDecorator.leppStartUrl)
      case "NO_ACTIONS"         => Some(configDecorator.leppPaymentsUrl)
      case _                    => None
    }
}
