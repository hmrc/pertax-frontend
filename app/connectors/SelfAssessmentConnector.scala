/*
 * Copyright 2021 HM Revenue & Customs
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

package connectors

import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import controllers.auth.requests.{AuthenticatedRequest, UserRequest}
import javax.inject.Inject
import metrics.HasMetrics
import models.{SaEnrolmentRequest, SaEnrolmentResponse}
import play.api.http.Status.{BAD_REQUEST, OK}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class SelfAssessmentConnector @Inject()(http: HttpClient, configDecorator: ConfigDecorator, val metrics: Metrics)(
  implicit ec: ExecutionContext)
    extends HasMetrics {

  def enrolForSelfAssessment(saEnrolmentRequest: SaEnrolmentRequest)(
    implicit request: UserRequest[_],
    hc: HeaderCarrier): Future[Option[SaEnrolmentResponse]] = {
    val url = s"${configDecorator.addTaxesFrontendUrl}/internal/self-assessment/enrol-for-sa"
    withMetricsTimer("enrol-for-self-assessment") { timer =>
      http.POST[SaEnrolmentRequest, Option[SaEnrolmentResponse]](url, saEnrolmentRequest) map {
        case res @ Some(_) =>
          timer.completeTimerAndIncrementSuccessCounter()
          res
        case res =>
          timer.completeTimerAndIncrementFailedCounter()
          res
      } recover {
        case _: Exception =>
          timer.completeTimerAndIncrementFailedCounter()
          None
      }
    }
  }
}
