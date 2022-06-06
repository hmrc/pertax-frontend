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
import models.BreathingSpaceIndicator
import play.api.Logging
import uk.gov.hmrc.http.HttpReads.Implicits._
import play.api.http.Status._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._

import java.util.UUID.randomUUID
import scala.concurrent.{ExecutionContext, Future}

class BreathingSpaceConnector @Inject() (http: HttpClient, configDecorator: ConfigDecorator, val metrics: Metrics)
    extends HasMetrics with Logging {

  val baseUrl = configDecorator.breathingSpaceIfProxyService

  def getBreathingSpaceIndicator(
    nino: Nino
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, Boolean] = {
    val url = s"$baseUrl/individuals/breathing-space/NINO/$nino/memorandum"
    implicit val bsHeaderCarrier: HeaderCarrier = hc
      .withExtraHeaders(
        "CorrelationId" -> randomUUID.toString
      )
    withMetricsTimer("get-breathing-space-indicator") { timer =>
      EitherT(
        http
          .GET[Either[UpstreamErrorResponse, HttpResponse]](url)(implicitly, bsHeaderCarrier, implicitly)
          .map {
            case response @ Right(_) =>
              timer.completeTimerAndIncrementSuccessCounter()
              response map { value =>
                value.json.as[BreathingSpaceIndicator].breathingSpaceIndicator
              }
            case Left(error) if error.statusCode == NOT_FOUND || error.statusCode == UNPROCESSABLE_ENTITY =>
              timer.completeTimerAndIncrementSuccessCounter()
              logger.info(error.message)
              Left(error)
            case Left(error) if error.statusCode >= 499 || error.statusCode == TOO_MANY_REQUESTS =>
              timer.completeTimerAndIncrementSuccessCounter()
              logger.error(error.message)
              Left(error)
            case Left(error) =>
              timer.completeTimerAndIncrementSuccessCounter()
              logger.error(error.message, error)
              Left(error)
          } recover {
          case exception: HttpException =>
            timer.completeTimerAndIncrementSuccessCounter()
            logger.error(exception.message)
            Left(UpstreamErrorResponse(exception.message, 502, 502))
          case exception: Exception =>
            throw exception
        }
      )
    }
  }
}
