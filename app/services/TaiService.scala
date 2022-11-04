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

package services

import com.google.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import metrics._
import models._
import play.api.Logging
import play.api.http.Status._
import services.http.SimpleHttp
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.Future

sealed trait TaxComponentsResponse
case class TaxComponentsSuccessResponse(taxComponents: TaxComponents) extends TaxComponentsResponse
case object TaxComponentsUnavailableResponse extends TaxComponentsResponse
case class TaxComponentsUnexpectedResponse(r: HttpResponse) extends TaxComponentsResponse
case class TaxComponentsErrorResponse(cause: Exception) extends TaxComponentsResponse
@Singleton
class TaiService @Inject() (val simpleHttp: SimpleHttp, val metrics: Metrics, servicesConfig: ServicesConfig)
    extends HasMetrics
    with Logging {

  lazy val taiUrl = servicesConfig.baseUrl("tai")

  /** Gets a list of tax components
    */

  // TODO
  def taxComponents(nino: Nino, year: Int)(implicit hc: HeaderCarrier): Future[TaxComponentsResponse] =
    withMetricsTimer("get-tax-components") { t =>
      simpleHttp.get[TaxComponentsResponse](s"$taiUrl/tai/$nino/tax-account/$year/tax-components")(
        onComplete = {
          case r if r.status >= 200 && r.status < 300 =>
            t.completeTimerAndIncrementSuccessCounter()
            TaxComponentsSuccessResponse(TaxComponents.fromJsonTaxComponents(r.json))

          case r if r.status == NOT_FOUND | r.status == BAD_REQUEST =>
            t.completeTimerAndIncrementSuccessCounter()
            logger.warn("Unable to find tax components from the tai-service")
            TaxComponentsUnavailableResponse

          case r =>
            t.completeTimerAndIncrementFailedCounter()
            logger.warn(s"Unexpected ${r.status} response getting tax components from the tai-service")
            TaxComponentsUnexpectedResponse(r)
        },
        onError = { case e =>
          t.completeTimerAndIncrementFailedCounter()
          logger.error("Error getting tax components from the tai-service")
          TaxComponentsErrorResponse(e)
        }
      )
    }
}
