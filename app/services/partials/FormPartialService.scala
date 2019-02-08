/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import metrics.HasMetrics
import play.api.Configuration
import play.api.Mode.Mode
import play.api.mvc.RequestHeader
import services.http.WsAllMethods
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.filters.SessionCookieCryptoFilter
import uk.gov.hmrc.play.partials.HtmlPartial
import util.EnhancedPartialRetriever

import scala.concurrent.Future


@Singleton
class FormPartialService @Inject() (val mode:Mode, val runModeConfiguration: Configuration, override val http: WsAllMethods, val metrics: Metrics, val configDecorator: ConfigDecorator, sessionCookieCryptoFilter: SessionCookieCryptoFilter) extends EnhancedPartialRetriever(sessionCookieCryptoFilter) with HasMetrics with ServicesConfig {

  def getNationalInsurancePartial(implicit request: RequestHeader): Future[HtmlPartial] = {
    loadPartial(configDecorator.nationalInsuranceFormPartialLinkUrl)
  }

  def getSelfAssessmentPartial(implicit request: RequestHeader): Future[HtmlPartial] = {
    loadPartial(configDecorator.selfAssessmentFormPartialLinkUrl)
  }

}
