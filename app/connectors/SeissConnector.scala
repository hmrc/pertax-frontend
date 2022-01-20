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

import com.google.inject.Inject
import com.kenshoo.play.metrics.Metrics
import config.ConfigDecorator
import metrics.HasMetrics
import models.{SeissModel, SeissRequest}
import play.api.i18n.Lang.logger
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpException, UpstreamErrorResponse}

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HttpReads.Implicits._

@Singleton
class SeissConnector @Inject() (
  http: HttpClient,
  implicit val ec: ExecutionContext,
  configDecorator: ConfigDecorator,
  val metrics: Metrics
) extends HasMetrics {

  def getClaims(utr: String)(implicit hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, List[SeissModel]]] = {
    val seissRequest = SeissRequest(utr)

    withMetricsTimer("Get-seiss-claims") { timer =>
      http
        .POST[SeissRequest, Either[UpstreamErrorResponse, List[SeissModel]]](
          s"${configDecorator.seissUrl}/self-employed-income-support/get-claims",
          seissRequest
        )
        .map {
          case Right(response) =>
            timer.completeTimerAndIncrementSuccessCounter()
            Right(response)
          case Left(error) if error.statusCode >= 499 || error.statusCode == 429 =>
            timer.completeTimerAndIncrementFailedCounter()
            logger.error(error.message)
            Left(error)
          case Left(error) =>
            timer.completeTimerAndIncrementFailedCounter()
            logger.error(error.message, error)
            Left(error)
        } recover {
        case exception: HttpException =>
          timer.completeTimerAndIncrementFailedCounter()
          logger.error(exception.message)
          Left(UpstreamErrorResponse(exception.message, 502, 502))
        case exception =>
          timer.completeTimerAndIncrementFailedCounter()
          throw exception
      }
    }
  }
}
