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

package services

import javax.inject.{Inject, Singleton}

import com.kenshoo.play.metrics.Metrics
import metrics.HasMetrics
import models.UserDetails
import play.api.Logger
import services.http.SimpleHttp

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class UserDetailsService @Inject() (val simpleHttp: SimpleHttp, val metrics: Metrics) extends HasMetrics {

  def getUserDetails(userDetailsUri: String)(implicit hc: HeaderCarrier): Future[Option[UserDetails]] = {

    withMetricsTimer("get-user-details") { t =>

      simpleHttp.get[Option[UserDetails]](userDetailsUri)(
        onComplete = {
          case r if r.status >= 200 && r.status < 300 =>
            t.completeTimerAndIncrementSuccessCounter()
            r.json.asOpt[UserDetails]

          case r =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.error(s"Unexpected ${r.status} response code getting user-details record")
            None
        },
        onError = {
          case e =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.error("Error getting user-details record", e)
            None
        }
      )
    }
  }
}
