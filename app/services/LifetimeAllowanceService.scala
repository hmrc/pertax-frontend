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
import models.LtaProtections
import play.api.{Configuration, Logger}
import play.api.Mode.Mode
import services.http.SimpleHttp
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}


@Singleton
class LifetimeAllowanceService @Inject() (val mode:Mode, val runModeConfiguration: Configuration, val simpleHttp: SimpleHttp, val metrics: Metrics) extends ServicesConfig with HasMetrics {

  lazy val lifetimeAllowanceUrl = baseUrl("pensions-lifetime-allowance")

  def getCount(nino: Nino)(implicit hc: HeaderCarrier, rds: HttpReads[LtaProtections]): Future[Option[Int]] = {
    withMetricsTimer("has-lta-response") { t =>

      simpleHttp.get[Option[Int]](lifetimeAllowanceUrl + s"/protect-your-lifetime-allowance/individuals/$nino/protections/count")(
        onComplete = {
          case r if r.status >= 200 && r.status < 300 =>
            t.completeTimerAndIncrementSuccessCounter()
            Some((r.json.as[LtaProtections]).count)

          case r =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn(s"Unexpected ${r.status} response getting lifetime allowance protections count from LTA service")
            None
        },
        onError = {
          case e =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn("Error getting lifetime allowance protections count from LTA service", e)
            None
        }
      )
    }
  }

  def hasLtaProtection(nino: Nino)(implicit hc: HeaderCarrier, rds: HttpReads[LtaProtections]): Future[Boolean] = getCount(nino) map {
    case (Some(0) | None) => false
    case _ => true
  }
}
