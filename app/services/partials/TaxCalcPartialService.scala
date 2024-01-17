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

package services.partials

import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import connectors.EnhancedPartialRetriever
import models.SummaryCardPartial
import models.admin.TaxcalcMakePaymentLinkToggle
import play.api.mvc.RequestHeader
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxCalcPartialService @Inject() (
  configDecorator: ConfigDecorator,
  enhancedPartialRetriever: EnhancedPartialRetriever,
  featureFlagService: FeatureFlagService
)(implicit executionContext: ExecutionContext) {
  def getTaxCalcPartial(implicit request: RequestHeader): Future[Seq[SummaryCardPartial]] =
    featureFlagService.get(TaxcalcMakePaymentLinkToggle).flatMap { toggle =>
      if (!toggle.isEnabled) {
        Future.successful(Nil)
      } else {
        enhancedPartialRetriever.loadPartialSeqSummaryCard(
          url = configDecorator.taxCalcFormPartialLinkUrl,
          timeoutInMilliseconds = configDecorator.taxCalcPartialTimeoutInMilliseconds
        )
      }
    }
}