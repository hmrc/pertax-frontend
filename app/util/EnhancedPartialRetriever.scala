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

package util

import com.codahale.metrics.Timer
import com.google.inject.Inject
import metrics.{Metrics, MetricsEnumeration}
import play.api.Logging
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.{HttpClient, HttpException}
import uk.gov.hmrc.play.partials.HtmlPartial._
import uk.gov.hmrc.play.partials.{HeaderCarrierForPartialsConverter, HtmlPartial}

import scala.concurrent.{ExecutionContext, Future}

class EnhancedPartialRetriever @Inject() (
  metrics: Metrics,
  http: HttpClient,
  headerCarrierForPartialsConverter: HeaderCarrierForPartialsConverter
) extends Logging {

  def loadPartial(url: String)(implicit request: RequestHeader, ec: ExecutionContext): Future[HtmlPartial] = {
    val timerContext: Timer.Context =
      metrics.startTimer(MetricsEnumeration.LOAD_PARTIAL)

    implicit val hc = headerCarrierForPartialsConverter.fromRequestWithEncryptedCookie(request)

    http.GET[HtmlPartial](url) map {
      case partial: HtmlPartial.Success =>
        timerContext.stop()
        metrics.incrementSuccessCounter(MetricsEnumeration.LOAD_PARTIAL)
        partial
      case partial: HtmlPartial.Failure =>
        timerContext.stop()
        logger.error(s"Failed to load partial from $url, partial info: $partial")
        metrics.incrementFailedCounter(MetricsEnumeration.LOAD_PARTIAL)
        partial
    } recover { case e =>
      timerContext.stop()
      metrics.incrementFailedCounter(MetricsEnumeration.LOAD_PARTIAL)
      e match {
        case ex: HttpException =>
          HtmlPartial.Failure(Some(ex.responseCode))
        case _                 =>
          HtmlPartial.Failure(None)
      }
    }
  }
}
