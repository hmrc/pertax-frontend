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
import metrics._
import models.{NotReconciled, TaxCalculation, TaxYearReconciliation}
import play.api.{Configuration, Environment, Logger}
import play.api.Mode.Mode
import play.api.http.Status._
import services.http.{SimpleHttp, WsAllMethods}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.util.control.NonFatal

sealed trait TaxCalculationResponse
case class TaxCalculationSuccessResponse(taxCalculation: TaxCalculation) extends TaxCalculationResponse
case object TaxCalculationNotFoundResponse extends TaxCalculationResponse
case class TaxCalculationUnexpectedResponse(r: HttpResponse) extends TaxCalculationResponse
case class TaxCalculationErrorResponse(cause: Exception) extends TaxCalculationResponse
@Singleton
class TaxCalculationService @Inject()(
  environment: Environment,
  configuration: Configuration,
  val simpleHttp: SimpleHttp,
  val metrics: Metrics,
  val http: WsAllMethods)(implicit ec: ExecutionContext)
    extends ServicesConfig with HasMetrics {

  val mode: Mode = environment.mode
  val runModeConfiguration: Configuration = configuration
  lazy val taxCalcUrl = baseUrl("taxcalc")

  /**
    * Gets a tax calc summary
    */
  def getTaxCalculation(nino: Nino, year: Int)(implicit hc: HeaderCarrier): Future[TaxCalculationResponse] =
    withMetricsTimer("get-taxcalc-summary") { t =>
      simpleHttp.get[TaxCalculationResponse](s"$taxCalcUrl/taxcalc/$nino/taxSummary/$year")(
        onComplete = {

          case r if r.status >= 200 && r.status < 300 =>
            Logger.debug(r.body)
            t.completeTimerAndIncrementSuccessCounter()
            TaxCalculationSuccessResponse(r.json.as[TaxCalculation])

          case r if r.status == NOT_FOUND =>
            Logger.debug(r.body)
            t.completeTimerAndIncrementSuccessCounter()
            TaxCalculationNotFoundResponse

          case r =>
            Logger.debug(r.body)
            t.completeTimerAndIncrementFailedCounter()
            Logger.debug(s"Unexpected ${r.status} response getting tax calculation from tax-calculation-service")
            TaxCalculationUnexpectedResponse(r)
        },
        onError = { e =>
          Logger.debug(e.toString)
          t.completeTimerAndIncrementFailedCounter()
          Logger.warn("Error getting tax calculation from tax-calculation-service", e)
          TaxCalculationErrorResponse(e)
        }
      )
    }

  def getTaxYearReconciliations(nino: Nino, startYear: Int, endYear: Int)(
    implicit headerCarrier: HeaderCarrier): Future[List[TaxYearReconciliation]] =
    http
      .GET[List[TaxYearReconciliation]](s"$taxCalcUrl/taxcalc/$nino/$startYear/$endYear/reconciliations")
      .recover {
        case NonFatal(e) =>
          Logger.debug(s"An exception was thrown by taxcalc reconciliations: ${e.getMessage}")
          (startYear to endYear).toList map {
            TaxYearReconciliation(_, NotReconciled)
          }
      }
}
