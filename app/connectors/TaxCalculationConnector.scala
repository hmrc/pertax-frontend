/*
 * Copyright 2022 HM Revenue & Customs
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

import cats.data.EitherT
import com.google.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import metrics.HasMetrics
import models.TaxYearReconciliation
import play.api.Logging
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxCalculationConnector @Inject() (
  val metrics: Metrics,
  val http: HttpClient,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse
)(implicit ec: ExecutionContext)
    extends HasMetrics
    with Logging {

  lazy val taxCalcUrl = servicesConfig.baseUrl("taxcalc")

  def getTaxYearReconciliations(
    nino: Nino
  )(implicit headerCarrier: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, List[TaxYearReconciliation]] =
    withMetricsTimer("get-tax-year-reconciliations") { t =>
      httpClientResponse
        .read(
          http.GET[Either[UpstreamErrorResponse, HttpResponse]](s"$taxCalcUrl/taxcalc/$nino/reconciliations")
        )
        .bimap(
          error => {
            t.completeTimerAndIncrementFailedCounter()
            error
          },
          response => {
            t.completeTimerAndIncrementSuccessCounter()
            response.json.as[List[TaxYearReconciliation]]
          }
        )
    }
}
