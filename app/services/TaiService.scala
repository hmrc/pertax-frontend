/*
 * Copyright 2017 HM Revenue & Customs
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
import metrics._
import models._
import play.api.http.Status._
import play.api.Logger
import services.http.SimpleHttp
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }


sealed trait TaxSummaryResponse
case class TaxSummarySuccessResponse(taxSummary: TaxSummary) extends TaxSummaryResponse
case object TaxSummaryNotFoundResponse extends TaxSummaryResponse
case class TaxSummaryUnexpectedResponse(r: HttpResponse) extends TaxSummaryResponse
case class TaxSummaryErrorResponse(cause: Exception) extends TaxSummaryResponse


@Singleton
class TaiService @Inject() (val simpleHttp: SimpleHttp, val metrics: Metrics) extends ServicesConfig with HasMetrics {

  lazy val taiUrl = baseUrl("tai")

  /**
    * Gets a tax summary
    */
  def taxSummary(nino: Nino, year: Int)(implicit hc: HeaderCarrier): Future[TaxSummaryResponse] = {
    withMetricsTimer("get-tax-summary") { t =>

      simpleHttp.get[TaxSummaryResponse](s"$taiUrl/tai/$nino/tax-summary/$year")(
        onComplete = {
          case r if r.status >= 200 && r.status < 300 =>
            t.completeTimerAndIncrementSuccessCounter()
            TaxSummarySuccessResponse(TaxSummary.fromJsonTaxSummaryDetails(r.json))

          case r if r.status == NOT_FOUND =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn("Unable to find tax summary record from the tai-service")
            TaxSummaryNotFoundResponse

          case r =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn(s"Unexpected ${r.status} response getting tax summary record from the tai-service")
            TaxSummaryUnexpectedResponse(r)
        },
        onError = {
          case e =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.error("Error getting tax summary record from the tai-service", e)
            TaxSummaryErrorResponse(e)
        }
      )
    }
  }
}
