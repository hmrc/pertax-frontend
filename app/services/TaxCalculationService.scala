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

package services

import com.google.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import metrics._
import models.{TaxCalculation, TaxYearReconciliation}
import play.api.Logger
import services.http.SimpleHttp
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

sealed trait TaxCalculationResponse
case class TaxCalculationSuccessResponse(taxCalculation: TaxCalculation) extends TaxCalculationResponse
case object TaxCalculationNotFoundResponse extends TaxCalculationResponse
case class TaxCalculationUnexpectedResponse(r: HttpResponse) extends TaxCalculationResponse
case class TaxCalculationErrorResponse(cause: Exception) extends TaxCalculationResponse
@Singleton
class TaxCalculationService @Inject()(
  val simpleHttp: SimpleHttp,
  val metrics: Metrics,
  val http: HttpClient,
  servicesConfig: ServicesConfig)(implicit ec: ExecutionContext)
    extends HasMetrics {

  lazy val taxCalcUrl = servicesConfig.baseUrl("taxcalc")

  def getTaxYearReconciliations(nino: Nino)(
    implicit headerCarrier: HeaderCarrier): Future[List[TaxYearReconciliation]] =
    withMetricsTimer("get-tax-year-reconciliations") { t =>
      http
        .GET[List[TaxYearReconciliation]](s"$taxCalcUrl/taxcalc/$nino/reconciliations") map { result =>
        t.completeTimerAndIncrementSuccessCounter()
        result
      } recover {
        case NonFatal(e) =>
          t.completeTimerAndIncrementFailedCounter()
          Logger.error(s"An exception was thrown by taxcalc reconciliations: ${e.getMessage}")
          Nil
      }
    }
}
