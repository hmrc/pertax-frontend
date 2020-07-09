/*
 * Copyright 2020 HM Revenue & Customs
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
import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import metrics.HasMetrics
import play.api.mvc.RequestHeader
import uk.gov.hmrc.play.bootstrap.filters.frontend.crypto.SessionCookieCrypto
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.partials.HtmlPartial
import util.EnhancedPartialRetriever

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FormPartialService @Inject()(
  override val http: HttpClient,
  val metrics: Metrics,
  val configDecorator: ConfigDecorator,
  sessionCookieCrypto: SessionCookieCrypto,
)(implicit executionContext: ExecutionContext)
    extends EnhancedPartialRetriever(sessionCookieCrypto) with HasMetrics {

  def getNationalInsurancePartial(implicit request: RequestHeader): Future[HtmlPartial] =
    loadPartial(configDecorator.nationalInsuranceFormPartialLinkUrl)

  def getSelfAssessmentPartial(implicit request: RequestHeader): Future[HtmlPartial] =
    loadPartial(configDecorator.selfAssessmentFormPartialLinkUrl)

}
