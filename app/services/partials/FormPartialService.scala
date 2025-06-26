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
import models.admin.DfsFormsFrontendAvailabilityToggle
import play.api.mvc.RequestHeader
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.partials.HtmlPartial

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FormPartialService @Inject() (
  configDecorator: ConfigDecorator,
  enhancedPartialRetriever: EnhancedPartialRetriever,
  featureFlagService: FeatureFlagService
)(implicit executionContext: ExecutionContext) {

  def getNationalInsurancePartial(implicit request: RequestHeader): Future[HtmlPartial] =
    featureFlagService.get(DfsFormsFrontendAvailabilityToggle).flatMap { toggle =>
      if (!toggle.isEnabled) {
        Future.successful(HtmlPartial.Failure(None, "dfs-digital-form-frontend is shuttered"))
      } else {
        enhancedPartialRetriever.loadPartial(
          url = configDecorator.nationalInsuranceFormPartialLinkUrl,
          timeoutInMilliseconds = configDecorator.dfsPartialTimeoutInMilliseconds
        )
      }
    }

  def getSelfAssessmentPartial(implicit request: RequestHeader): Future[HtmlPartial] =
    featureFlagService.get(DfsFormsFrontendAvailabilityToggle).flatMap { toggle =>
      if (!toggle.isEnabled) {
        Future.successful(HtmlPartial.Failure(None, "dfs-digital-form-frontend is shuttered"))
      } else {
        enhancedPartialRetriever.loadPartial(
          url = configDecorator.selfAssessmentFormPartialLinkUrl,
          timeoutInMilliseconds = configDecorator.dfsPartialTimeoutInMilliseconds
        )
      }
    }

  def getNISPPartial(implicit request: RequestHeader): Future[HtmlPartial] =
    featureFlagService.get(DfsFormsFrontendAvailabilityToggle).flatMap { toggle =>
      if (!toggle.isEnabled) {
        Future.successful(HtmlPartial.Failure(None, "dfs-digital-form-frontend is shuttered"))
      } else {
        enhancedPartialRetriever.loadPartial(configDecorator.nationalInsuranceFormPartialLinkUrl)
      }
    }
}
