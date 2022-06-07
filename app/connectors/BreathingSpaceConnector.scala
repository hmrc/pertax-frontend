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
import cats.implicits.catsStdInstancesForFuture
import com.codahale.metrics.Timer
import com.google.common.util.concurrent.RateLimiter
import com.google.inject.Inject
import metrics.{Metrics, MetricsEnumeration}
import models.BreathingSpaceIndicator
import play.api.Logging
import play.api.http.Status._
import play.api.libs.json.Format.GenericFormat
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpReadsInstances.readEitherOf
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.{Limiters, Throttle, Timeout}

import java.util.UUID.randomUUID
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class BreathingSpaceConnector @Inject() (
  val httpClient: HttpClient,
  val metrics: Metrics,
  servicesConfig: ServicesConfig,
  limiters: Limiters
) extends Throttle with Timeout with Logging {

  lazy val baseUrl = servicesConfig.baseUrl("breathing-space-if-proxy")
  lazy val timeoutInSec =
    servicesConfig.getInt("feature.breathing-Space-indicator.timeoutInSec")
  val rateLimiter: RateLimiter = limiters.rateLimiterForGetBreathingSpaceIndicator
  val metricName = MetricsEnumeration.GET_BREATHING_SPACE_INDICATOR

  def getBreathingSpaceIndicator(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, Boolean] = {

    val timerContext: Timer.Context = metrics.startTimer(metricName)
    val url = s"$baseUrl/$nino/memorandum"
    implicit val bsHeaderCarrier: HeaderCarrier = hc
      .withExtraHeaders(
        "Correlation-Id" -> randomUUID.toString
      )

    val result =
      withThrottle {
        withTimeout(timeoutInSec seconds) {
          httpClient
            .GET[Either[UpstreamErrorResponse, HttpResponse]](url)(implicitly, bsHeaderCarrier, implicitly)
            .map { response =>
              timerContext.stop()
              response
            }
        }
      }

    val eitherResponse = EitherT(
      result
        .map {
          case Right(response) =>
            metrics.incrementSuccessCounter(metricName)
            Right(response)
          case Left(error) if error.statusCode == NOT_FOUND || error.statusCode == TOO_MANY_REQUESTS =>
            metrics.incrementSuccessCounter(metricName)
            logger.info(error.message)
            Left(error)
          case Left(error) if error.statusCode >= 400 && error.statusCode <= 499 =>
            metrics.incrementSuccessCounter(metricName)
            logger.error(error.message)
            throw new HttpException(error.message, error.statusCode)
          case Left(error) =>
            metrics.incrementSuccessCounter(metricName)
            logger.error(error.message, error)
            Left(error)
        }
    )

    eitherResponse.map { value =>
      value.json.as[BreathingSpaceIndicator].breathingSpaceIndicator
    }

  }
}
