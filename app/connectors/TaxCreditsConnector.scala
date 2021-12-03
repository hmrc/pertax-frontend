/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.http.Status.INTERNAL_SERVER_ERROR
import services.http.SimpleHttp
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReadsInstances.readEitherOf
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpException, HttpResponse, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

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
    withMetricsTimer("check-for-tax-credits") { t =>
      EitherT(
        http
          .GET[Either[UpstreamErrorResponse, HttpResponse]](s"$taxCreditsUrl/tcs/$nino/dashboard-data")
          .map {
            case response @ Right(_) => response
            case Left(error)         => Left(UpstreamErrorResponse(error.message, error.statusCode))
          }
          .recover { case error: HttpException =>
            Left(UpstreamErrorResponse(error.message, error.responseCode))
          }
      )

//      http
//        .GET(s"$taxCreditsUrl/tcs/$nino/dashboard-data") map { result =>
//        result.status
//      } recover { case NonFatal(e) =>
//        t.completeTimerAndIncrementFailedCounter()
//        logger.error(s"An exception was thrown by tax credits dashboard data: ${e.getMessage}")
//        INTERNAL_SERVER_ERROR
//      }
    }
}
