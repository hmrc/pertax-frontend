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
import com.google.inject.Inject
import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import metrics.HasMetrics
import play.api.Logging
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReadsInstances.readEitherOf
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpException, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

class TaxCreditsConnector @Inject() (
  val http: HttpClient,
  configDecorator: ConfigDecorator,
  val metrics: Metrics
)(implicit ec: ExecutionContext)
    extends HasMetrics with Logging {

  lazy val taxCreditsUrl: String = configDecorator.tcsBrokerHost

  def checkForTaxCredits(
    nino: Nino
  )(implicit headerCarrier: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
    withMetricsTimer("check-for-tax-credits") { timer =>
      EitherT(
        http
          .GET[Either[UpstreamErrorResponse, HttpResponse]](s"$taxCreditsUrl/tcs/$nino/dashboard-data")
          .map {
            case response @ Right(_) =>
              timer.completeTimerAndIncrementSuccessCounter()
              response
            case Left(error) if error.statusCode == NOT_FOUND =>
              timer.completeTimerAndIncrementSuccessCounter()
              Left(UpstreamErrorResponse(error.message, error.statusCode))
            case Left(error) if error.statusCode >= INTERNAL_SERVER_ERROR =>
              timer.completeTimerAndIncrementFailedCounter()
              logger.error(error.message)
              Left(UpstreamErrorResponse(error.message, error.statusCode))
            case Left(error) =>
              timer.completeTimerAndIncrementFailedCounter()
              logger.error(error.message, error)
              Left(UpstreamErrorResponse(error.message, error.statusCode))
          }
          .recover { case exception: HttpException =>
            timer.completeTimerAndIncrementFailedCounter()
            logger.error(exception.message)
            Left(UpstreamErrorResponse(exception.message, exception.responseCode))
          }
      )
    }
}
