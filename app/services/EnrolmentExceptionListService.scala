/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import com.kenshoo.play.metrics.Metrics
import metrics.HasMetrics
import play.api.Logger
import play.api.http.Status._
import services.http.SimpleHttp
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier


@Singleton
class EnrolmentExceptionListService @Inject() (val simpleHttp: SimpleHttp, val metrics: Metrics) extends ServicesConfig with HasMetrics {

  val enrolmentExceptionListUrl = baseUrl("enrolment-exception-list")

  def isAccountIdentityVerificationExempt(saUtr: SaUtr)(implicit hc: HeaderCarrier): Future[Boolean] = {
    withMetricsTimer("get-enrolment-exception") { t =>

      simpleHttp.get(s"$enrolmentExceptionListUrl/enrolment-exception-list/api/ir-sa/utr/$saUtr")(
        onComplete = {
          case r if r.status == OK =>
            t.completeTimerAndIncrementSuccessCounter()
            true
          case r if r.status == NOT_FOUND =>
            t.completeTimerAndIncrementSuccessCounter()
            Logger.warn("Unable to verify if account is exempt from identity verification in enrolment exception list service")
            false
          case r =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn(s"Unexpected ${r.status} response from enrolment-exception-list service")
            false
        },
        onError = {
          case e =>
            Logger.warn("Error calling enrolment exception-list-service", e)
            t.completeTimerAndIncrementFailedCounter()
            false
        }
      )
    }
  }
}
