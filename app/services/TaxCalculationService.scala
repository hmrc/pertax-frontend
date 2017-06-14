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
import models.TaxCalculation
import play.api.Logger
import play.api.http.Status._
import services.http.SimpleHttp
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http._

import scala.concurrent.Future


sealed trait TaxCalculationResponse
case class TaxCalculationSuccessResponse(taxCalculation: TaxCalculation) extends TaxCalculationResponse
case object TaxCalculationNotFoundResponse extends TaxCalculationResponse
case class TaxCalculationUnexpectedResponse(r: HttpResponse) extends TaxCalculationResponse
case class TaxCalculationErrorResponse(cause: Exception) extends TaxCalculationResponse


@Singleton
class TaxCalculationService @Inject() (val simpleHttp: SimpleHttp, val metrics: Metrics) extends ServicesConfig with HasMetrics {

  lazy val taxCalcUrl = baseUrl("taxcalc")
  
  /**
   * Gets a tax calc summary
   */
  def getTaxCalculation(nino: Nino, year: Int)(implicit hc: HeaderCarrier): Future[TaxCalculationResponse] = {
    withMetricsTimer("get-taxcalc-summary") { t =>

      simpleHttp.get[TaxCalculationResponse](s"$taxCalcUrl/taxcalc/$nino/taxSummary/$year") (
        onComplete = {
          case r if r.status >= 200 && r.status < 300 =>
            t.completeTimerAndIncrementSuccessCounter()
            TaxCalculationSuccessResponse(r.json.as[TaxCalculation])

          case r if r.status == NOT_FOUND =>
            t.completeTimerAndIncrementSuccessCounter()
            TaxCalculationNotFoundResponse

          case r =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn(s"Unexpected ${r.status} response getting tax calculation from tax-calculation-service")
            TaxCalculationUnexpectedResponse(r)
        },
        onError = {
          case e =>
            t.completeTimerAndIncrementFailedCounter()
            Logger.warn("Error getting tax calculation from tax-calculation-service", e)
            TaxCalculationErrorResponse(e)
        }
      )
    }
  }
}
